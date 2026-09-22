package com.opencrm.crm.event;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Quote acceptance from Kafka {@code cpq.quote.status.accepted} (ADR-018 D8). This replaces the Redis
 * {@code quote:accepted} channel, which loses messages sent while crm-service is down and used to be published inside
 * quote-service's accept transaction.
 *
 * <p>During the dual run both channels are live. {@link QuoteAcceptedDealProcessor} claims the quote UUID in
 * {@code processed_events} inside the deal transaction, so a deal is marked WON once, whichever channel arrives first.
 *
 * <p>Off by default ({@code opencrm.crm.quote-accepted.kafka.enabled}). Turn it on together with quote-service's
 * outbox relay, which is what puts the events on the topic.
 *
 * <p>Error handling:
 * <ul>
 *   <li>An event without a deal (quotes need not have one) is skipped.</li>
 *   <li>A missing deal is logged and skipped. Nothing is claimed, so a redelivery can still apply it.</li>
 *   <li>An unreadable payload is not retryable and goes to {@code cpq.quote.status.accepted.DLT} (D7).</li>
 *   <li>A database failure propagates, so the shared error handler retries it.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "opencrm.crm.quote-accepted.kafka.enabled", havingValue = "true")
public class QuoteAcceptedKafkaListener {

    /** Kafka consumer group of crm-service on the quote-accepted topic. */
    public static final String CONSUMER_GROUP = "crm-service";

    private final ObjectMapper objectMapper;
    private final QuoteAcceptedDealProcessor quoteAcceptedDealProcessor;

    @KafkaListener(topics = KafkaTopics.QUOTE_STATUS_ACCEPTED, groupId = CONSUMER_GROUP)
    public void onQuoteAccepted(String payload) {
        QuoteAcceptedCrmMessage message = toCrmMessage(parse(payload));
        String dealExternalId = message.getDealExternalId();
        if (dealExternalId == null || dealExternalId.isBlank()) {
            log.debug("Quote {} accepted without a CRM deal; nothing to update", message.getQuoteNumber());
            return;
        }
        quoteAcceptedDealProcessor.applyWinFromQuote(message, dealExternalId);
    }

    private QuoteAcceptedEvent parse(String payload) {
        try {
            QuoteAcceptedEvent event = objectMapper.readValue(payload, QuoteAcceptedEvent.class);
            if (event == null || event.getQuoteId() == null || event.getQuoteId().isBlank()) {
                throw new NonRetryableEventException("Quote accepted event lacks quoteId: " + payload);
            }
            return event;
        } catch (JacksonException e) {
            throw new NonRetryableEventException("Unreadable quote accepted event", e);
        }
    }

    /**
     * Maps the Kafka contract onto the fields the deal update uses. {@code quoteUuid} is the event's {@code quoteId},
     * which quote-service sets to the quote UUID: the same value the Redis message carries, so both channels claim the
     * same dedupe key.
     */
    static QuoteAcceptedCrmMessage toCrmMessage(QuoteAcceptedEvent event) {
        QuoteAcceptedEvent.PricingSnapshot pricing = event.getPricing();
        QuoteAcceptedEvent.DealSnapshot deal = event.getDeal();
        return QuoteAcceptedCrmMessage.builder()
                .quoteUuid(event.getQuoteId())
                .quoteNumber(event.getQuote() != null ? event.getQuote().getQuoteNumber() : null)
                .customerExternalId(event.getCustomerId())
                .dealExternalId(deal != null ? deal.getDealExternalId() : null)
                .dealType(deal != null ? deal.getDealType() : null)
                .totalAmount(event.getTotalAmount())
                .currency(event.getCurrencyCode())
                .arr(pricing != null ? pricing.getArr() : null)
                .tcv(pricing != null ? pricing.getTcv() : null)
                .acv(pricing != null ? pricing.getAcv() : null)
                .durationInMonths(event.getTerms() != null && event.getTerms().getTermMonths() != null
                        ? event.getTerms().getTermMonths() : 0)
                .acceptedAt(event.getAcceptedAt())
                .build();
    }
}
