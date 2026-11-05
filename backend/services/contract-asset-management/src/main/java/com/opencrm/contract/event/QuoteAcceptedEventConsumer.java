package com.opencrm.contract.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.service.AcceptedQuoteSubscriptionService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


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

    private final ObjectMapper objectMapper;
    private final KafkaInbox inbox;
    private final AcceptedQuoteSubscriptionService acceptedQuotes;

    @Autowired(required = false)
    private LegacyOwnershipPolicy ownershipPolicy;

    @Autowired(required = false)
    private OmsSourceRegistry sourceRegistry;

    /** Set when the context scans {@link AmendmentRoutingFlag}. Absent means the code default, OFF. */
    @Autowired(required = false)
    private AmendmentRoutingFlag injectedRoutingFlag;

    /** Actuator supplies this in the running service. Tests that do not bind a registry skip the counters. */
    @Autowired(required = false)
    private MeterRegistry injectedMeters;

    private AmendmentRoutingFlag routingFlagOverride;
    private MeterRegistry meterOverride;

    /**
     * Test seam. Production leaves these unset and uses the injected beans.
     */
    void bindRouting(AmendmentRoutingFlag flag, MeterRegistry meters) {
        this.routingFlagOverride = flag;
        this.meterOverride = meters;
    }

    @KafkaListener(topics = KafkaTopics.QUOTE_STATUS_ACCEPTED, groupId = CONSUMER_GROUP)
    @Transactional
    public void handleQuoteAccepted(ConsumerRecord<String, String> record) {
        if (!inbox.claim(CONSUMER_GROUP, record)) {
            log.info("Skipping already-processed quote accepted event {}", KafkaInbox.eventIdOf(record));
            return;
        }
        QuoteAcceptedEvent event = parse(record.value());
        if (ownershipPolicy != null && !ownershipPolicy.legacyAllowed(objectMapper.valueToTree(event))) {
            return;
        }
        if (sourceRegistry != null) {
            try {
                if (!sourceRegistry.permitsLegacy(event.getQuoteId())) {
                    return;
                }
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw new IllegalStateException("Cannot verify quote source ownership", error);
            }
        }
        log.info("Received quote accepted event for quote: {}", event.getQuoteId());
        route(event);
    }

    /**
     * After claim and parse. Flag off or no event tenant selects legacy apply. There is no document path:
     * {@link AmendmentRoutingFlag#DOCUMENT_PATH_SELECTED} stays at zero, which is the alert condition
     * "document path selected".
     */
    private void route(QuoteAcceptedEvent event) {
        AmendmentRoutingDecision decision = routingFlag().decide(event);
        MeterRegistry registry = meters();
        if (registry != null) {
            registry.counter(AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED);
        }
        if (decision != AmendmentRoutingDecision.LEGACY) {
            if (registry != null) {
                registry.counter(AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED).increment();
            }
            log.error("document path selected quoteId={}; alert condition \"document path selected\" must not fire",
                    event.getQuoteId());
            throw new IllegalStateException("document path is not built");
        }
        if (registry != null) {
            registry.counter(AmendmentRoutingFlag.ROUTED_LEGACY).increment();
        }
        boolean documentIdPresent = event.getAmendmentDocumentId() != null
                && !event.getAmendmentDocumentId().isBlank();
        log.info("routed_legacy quoteId={} amendmentDocumentIdPresent={}", event.getQuoteId(), documentIdPresent);
        acceptedQuotes.apply(event);
    }

    private AmendmentRoutingFlag routingFlag() {
        if (routingFlagOverride != null) {
            return routingFlagOverride;
        }
        if (injectedRoutingFlag != null) {
            return injectedRoutingFlag;
        }
        return AmendmentRoutingFlag.off();
    }

    private MeterRegistry meters() {
        if (meterOverride != null) {
            return meterOverride;
        }
        return injectedMeters;
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
