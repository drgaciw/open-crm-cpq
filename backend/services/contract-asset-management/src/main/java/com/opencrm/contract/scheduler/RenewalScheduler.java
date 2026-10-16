package com.opencrm.contract.scheduler;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.repository.SubscriptionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly renewal-eligibility scan. Writes one outbox row per subscription term.
 *
 * <p>The row's aggregate id is {@link SubscriptionRenewalEligibleEvent#dedupeKey(String, LocalDate, LocalDate)}
 * (subscription id, term end, renewal window). {@code OutboxRelayPublisher} uses that aggregate id as the Kafka
 * record key. A later night finds the row and does not write another. No column was added: contract-asset
 * {@code V1_7} belongs to DRG-579 and {@code V1_8} to DRG-585.
 *
 * <p>The marker is the outbox row itself. The relay deletes a published or skipped row after its retention
 * (default 7 days, {@code opencrm.outbox.relay.retention}). While the relay is off, rows are kept. A subscription
 * that is still eligible can be published again only after that row is gone.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RenewalScheduler {

    static final String AGGREGATE_TYPE = "Subscription";

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final EntityManager entityManager;

    /**
     * Check for renewal-eligible subscriptions daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void checkRenewalEligibility() {
        log.info("Checking for renewal-eligible subscriptions");

        LocalDate today = LocalDate.now();
        List<Subscription> eligibleSubscriptions = subscriptionRepository.findRenewalEligible(today);

        for (Subscription subscription : eligibleSubscriptions) {
            try {
                String key = SubscriptionRenewalEligibleEvent.dedupeKey(
                        subscription.getId().toString(),
                        subscription.getEndDate(),
                        subscription.getRenewalDate());
                if (alreadyPublished(key)) {
                    log.info("Renewal eligible event already in the outbox for {}", key);
                    continue;
                }

                SubscriptionRenewalEligibleEvent event = new SubscriptionRenewalEligibleEvent(
                        subscription.getId().toString(),
                        subscription.getRenewalDate(),
                        subscription.getPlanName(),
                        subscription.getUsageBand(),
                        key
                );

                outboxEventPublisher.publish(
                        AGGREGATE_TYPE,
                        key,
                        EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1,
                        event
                );

                log.info("Published renewal eligible event for subscription: {}",
                         subscription.getSubscriptionNumber());

            } catch (Exception e) {
                log.error("Error publishing renewal event for subscription: {}",
                          subscription.getSubscriptionNumber(), e);
            }
        }

        log.info("Completed renewal eligibility check. Found {} eligible subscriptions",
                 eligibleSubscriptions.size());
    }

    /**
     * True when this term's row is already in the outbox, in any relay status. Matches the topic column and both
     * values {@code event_type} has held: the topic today, and the registry event type after the ADR-018 D4 contract step.
     */
    private boolean alreadyPublished(String dedupeKey) {
        String topic = EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.topic();
        String eventType = EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.eventType();
        Long count = entityManager.createQuery("""
                SELECT COUNT(e) FROM OutboxEvent e
                WHERE e.aggregateType = :aggregateType
                  AND e.aggregateId = :aggregateId
                  AND e.deleted = false
                  AND (e.topic = :topic OR e.eventType = :topic OR e.eventType = :eventType)
                """, Long.class)
                .setParameter("aggregateType", AGGREGATE_TYPE)
                .setParameter("aggregateId", dedupeKey)
                .setParameter("topic", topic)
                .setParameter("eventType", eventType)
                .getSingleResult();
        return count != null && count > 0;
    }
}
