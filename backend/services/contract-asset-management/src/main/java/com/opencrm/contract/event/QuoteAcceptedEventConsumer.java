package com.opencrm.contract.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Consumes quote accepted events and creates subscriptions.
 *
 * <p>ADR-018 D6:
 * <ul>
 *   <li><b>Idempotent.</b> A redelivery used to violate {@code UNIQUE(subscription_number)}; the exception was then
 *       retried and dropped. Now the {@link KafkaInbox} refuses an event already processed, and an existing
 *       {@code SUB-<quoteId>} makes a copy without an {@code eventId} a no-op.</li>
 *   <li><b>No external call in the listener transaction.</b> The ERP sync used to run here, so a later rollback
 *       (or a redelivery) could leave an ERP record for a subscription that was never committed, or sync twice. The
 *       subscription is now created with {@code erp_sync_status = QUEUED}; the {@code ErpSyncJob} syncs it to the
 *       ERP and activates it afterwards, with its own retry.</li>
 * </ul>
 * An unreadable payload is not retryable and goes to {@code cpq.quote.status.accepted.DLT} (D7).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuoteAcceptedEventConsumer {

    /** Kafka consumer group; also the inbox namespace. */
    public static final String CONSUMER_GROUP = "contract-asset-management";

    private final SubscriptionRepository subscriptionRepository;
    private final ObjectMapper objectMapper;
    private final KafkaInbox inbox;

    @KafkaListener(topics = KafkaTopics.QUOTE_STATUS_ACCEPTED, groupId = CONSUMER_GROUP)
    @Transactional
    public void handleQuoteAccepted(ConsumerRecord<String, String> record) {
        if (!inbox.claim(CONSUMER_GROUP, record)) {
            log.info("Skipping already-processed quote accepted event {}", KafkaInbox.eventIdOf(record));
            return;
        }
        QuoteAcceptedEvent event = parse(record.value());
        log.info("Received quote accepted event for quote: {}", event.getQuoteId());

        String subscriptionNumber = "SUB-" + event.getQuoteId();
        if (subscriptionRepository.existsBySubscriptionNumber(subscriptionNumber)) {
            log.info("Subscription {} already exists; quote accepted event is a duplicate", subscriptionNumber);
            return;
        }

        Subscription subscription = Subscription.builder()
                .subscriptionNumber(subscriptionNumber)
                .customerId(event.getCustomerId())
                .quoteId(event.getQuoteId())
                .status(SubscriptionStatus.PENDING)
                .planName("Standard Plan") // In production, extract from quote
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusYears(1))
                .renewalDate(LocalDate.now().plusYears(1).minusDays(30))
                .arr(event.getTotalAmount())
                .currencyCode(event.getCurrencyCode())
                .erpSyncStatus(ErpSyncStatus.QUEUED)
                .build();
        subscriptionRepository.save(subscription);

        log.info("Created subscription {} for quote {}; ERP sync queued", subscriptionNumber, event.getQuoteId());
    }

    private QuoteAcceptedEvent parse(String payload) {
        try {
            QuoteAcceptedEvent event = objectMapper.readValue(payload, QuoteAcceptedEvent.class);
            if (event == null || event.getQuoteId() == null || event.getQuoteId().isBlank()) {
                throw new NonRetryableEventException("Quote accepted event lacks quoteId: " + payload);
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException("Unreadable quote accepted event", e);
        }
    }
}
