package com.opencrm.quote.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventContractRegistry.EventContract;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.quote.quote.QuoteContinuationService;
import com.opencrm.quote.ramp.RampPricingProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code cpq.subscription.renewal.eligible} ({@link EventContractRegistry#SUBSCRIPTION_RENEWAL_ELIGIBLE_V1})
 * and opens one draft renewal quote.
 *
 * <p>Idempotent without a publisher dedupe key (DRG-545 may add one). The inbox claims the record's {@code eventId}
 * (or {@code topic-partition@offset}). A textual {@code dedupeKey}, {@code idempotencyKey} or {@code emissionKey} on
 * the payload is claimed as well, so a stable key collapses re-emissions. A new event id for the same subscription
 * window still creates nothing once a live renewal quote exists for that start date.
 *
 * <p>Registered only while continuation authoring is on ({@code opencrm.pricing.ramp.enabled}). That gate already
 * refuses every continuation write, including this one; the committed value stays false. While the listener is
 * absent the consumer group has no offset, so retained events are still available when the gate is turned on
 * ({@code auto-offset-reset: earliest}). Unknown payload fields are ignored.
 *
 * <p>No draft-quote event is emitted. {@code EventContractRegistry} has no such contract (the old
 * {@code cpq.quote.created} topic was removed), and this consumer does not add one, so there is no outbox write.
 * The system decision is the {@code commercial_decision_audit} row {@link QuoteContinuationService#draftRenewal}
 * inserts in this transaction.
 */
@Component
@ConditionalOnProperty(name = "opencrm.pricing.ramp.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionRenewalEligibleEventConsumer {

    /** Kafka consumer group; also the inbox namespace. */
    public static final String CONSUMER_GROUP = "quote-service-renewal";

    private static final EventContract CONTRACT = EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1;

    private final QuoteContinuationService continuations;
    private final KafkaInbox inbox;
    private final ObjectMapper objectMapper;
    private final RampPricingProperties ramp;

    @KafkaListener(topics = KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE, groupId = CONSUMER_GROUP)
    @Transactional
    public void onRenewalEligible(ConsumerRecord<String, String> record) {
        requireContract(record);
        Eligibility eligibility = parse(record.value());
        if (!ramp.isEnabled()) {
            log.info("Continuation authoring is disabled; renewal eligibility for subscription {} was not claimed",
                    eligibility.event().getSubscriptionId());
            return;
        }
        if (!inbox.claim(CONSUMER_GROUP, record)) {
            log.info("Skipping already-processed renewal eligibility {}", KafkaInbox.eventIdOf(record));
            return;
        }
        if (eligibility.dedupeKey() != null && !inbox.claimKey(CONSUMER_GROUP, dedupeClaim(eligibility.dedupeKey()))) {
            log.info("Skipping renewal eligibility {} already claimed by dedupe key", KafkaInbox.eventIdOf(record));
            return;
        }
        continuations.draftRenewal(eligibility.event(), eligibility.subscriptionNumber(), KafkaInbox.eventIdOf(record),
                eligibility.dedupeKey());
    }

    private void requireContract(ConsumerRecord<String, String> record) {
        if (!CONTRACT.topic().equals(record.topic())) {
            throw new NonRetryableEventException("Renewal eligibility arrived on " + record.topic()
                    + "; the registry contract is " + CONTRACT.topic());
        }
        String eventType = EventHeaders.value(record, EventHeaders.EVENT_TYPE);
        if (eventType != null && !CONTRACT.eventType().equals(eventType)) {
            throw new NonRetryableEventException("Expected event type " + CONTRACT.eventType() + " but received " + eventType);
        }
        String version = EventHeaders.value(record, EventHeaders.EVENT_VERSION);
        if (version != null && !Integer.toString(CONTRACT.eventVersion()).equals(version)) {
            throw new NonRetryableEventException("Unsupported renewal eligibility version " + version);
        }
        if (CONTRACT.payloadClass() != SubscriptionRenewalEligibleEvent.class) {
            throw new NonRetryableEventException("Renewal eligibility contract payload is not "
                    + SubscriptionRenewalEligibleEvent.class.getSimpleName());
        }
    }

    private Eligibility parse(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (node == null || !node.isObject()) {
                throw new NonRetryableEventException("Renewal eligibility payload is not an object");
            }
            SubscriptionRenewalEligibleEvent event = objectMapper.treeToValue(node, SubscriptionRenewalEligibleEvent.class);
            if (event == null || event.getSubscriptionId() == null || event.getSubscriptionId().isBlank()) {
                throw new NonRetryableEventException("Renewal eligibility event lacks subscriptionId");
            }
            return new Eligibility(event, firstText(node, "subscriptionNumber", "targetSubscriptionNumber"),
                    firstText(node, "dedupeKey", "idempotencyKey", "emissionKey"));
        } catch (NonRetryableEventException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new NonRetryableEventException("Unreadable renewal eligibility event", ex);
        }
    }

    private static String dedupeClaim(String dedupeKey) {
        String claim = "dedupe:" + dedupeKey;
        if (claim.length() > 200) {
            throw new NonRetryableEventException("Renewal dedupe key exceeds the inbox key length");
        }
        return claim;
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private record Eligibility(SubscriptionRenewalEligibleEvent event, String subscriptionNumber, String dedupeKey) {
    }
}
