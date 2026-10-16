package com.opencrm.contract.scheduler;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;

/** Nightly scan with a durable, atomic publication claim per subscription term and renewal window. */
@Component
@RequiredArgsConstructor
@Slf4j
public class RenewalScheduler {

    static final String AGGREGATE_TYPE = "Subscription";
    private static final String CLAIM = """
            INSERT INTO renewal_eligibility_publications (dedupe_key, subscription_id, term_end, renewal_window)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (dedupe_key) DO NOTHING
            """;

    private final SubscriptionRepository subscriptionRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;

    @Scheduled(cron = "0 0 2 * * *")
    public void checkRenewalEligibility() {
        List<Subscription> eligible = subscriptionRepository.findRenewalEligible(LocalDate.now());
        TransactionTemplate publication = new TransactionTemplate(transactions);
        publication.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        for (Subscription subscription : eligible) {
            try {
                publication.executeWithoutResult(status -> publishOnce(subscription));
            } catch (RuntimeException failure) {
                // A failed outbox write rolls its claim back before the next subscription is attempted.
                log.error("Error publishing renewal eligibility for {}", subscription.getSubscriptionNumber(), failure);
            }
        }
    }

    private void publishOnce(Subscription subscription) {
        String key = SubscriptionRenewalEligibleEvent.dedupeKey(subscription.getId().toString(),
                subscription.getEndDate(), subscription.getRenewalDate());
        if (jdbc.update(CLAIM, key, subscription.getId(), subscription.getEndDate(), subscription.getRenewalDate()) == 0) {
            return;
        }
        SubscriptionRenewalEligibleEvent event = new SubscriptionRenewalEligibleEvent(
                subscription.getId().toString(), subscription.getRenewalDate(), subscription.getPlanName(),
                subscription.getUsageBand(), key);
        outboxEventPublisher.publish(AGGREGATE_TYPE, key,
                EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1, event);
    }
}
