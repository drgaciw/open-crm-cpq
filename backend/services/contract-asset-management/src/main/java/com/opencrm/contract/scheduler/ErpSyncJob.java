package com.opencrm.contract.scheduler;

import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.contract.acl.ErpRateLimitException;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Syncs subscriptions created from quote-accepted events to the ERP, then activates them (ADR-018 D6). This used to run
 * inside the Kafka listener's transaction; now the listener only queues the work ({@code erp_sync_status = QUEUED}).
 *
 * <p>Per subscription: claim it with a compare-and-set {@code QUEUED → IN_PROGRESS} (one short transaction, so replicas
 * never sync the same row), call the ERP with no transaction open, then record the outcome in a second short
 * transaction:
 * <ul>
 *   <li>success: {@code SYNCED}, ERP id stored, subscription activated (the previous consumer behaviour);</li>
 *   <li>{@link ErpRateLimitException}: back to {@code QUEUED} for the next run;</li>
 *   <li>any other failure: {@code FAILED}, and the subscription stays PENDING, as before. An operator can retry by
 *       activating it through the API, which syncs again.</li>
 * </ul>
 * A claim left {@code IN_PROGRESS} by a crashed run returns to {@code QUEUED} after {@code stale-after}. The ERP call is
 * keyed by subscription number, so a sync repeated after such a crash must be an upsert on the ERP side.
 *
 * <p>API-created subscriptions ({@code PENDING}) are not touched: they sync when activated through the API.
 */
@Component
@ConditionalOnProperty(name = "opencrm.erp-sync.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ErpSyncJob {

    private final SubscriptionRepository repository;
    private final ErpAdapter erpAdapter;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final Duration staleAfter;

    @Autowired
    public ErpSyncJob(
            SubscriptionRepository repository,
            ErpAdapter erpAdapter,
            PlatformTransactionManager transactionManager,
            @Value("${opencrm.erp-sync.batch-size:20}") int batchSize,
            @Value("${opencrm.erp-sync.stale-after:PT10M}") Duration staleAfter) {
        this(repository, erpAdapter, new TransactionTemplate(transactionManager), Clock.systemUTC(), batchSize,
                staleAfter);
    }

    ErpSyncJob(SubscriptionRepository repository, ErpAdapter erpAdapter, TransactionTemplate tx, Clock clock,
               int batchSize, Duration staleAfter) {
        this.repository = repository;
        this.erpAdapter = erpAdapter;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
        this.staleAfter = staleAfter;
    }

    @Scheduled(fixedDelayString = "${opencrm.erp-sync.poll-interval:PT30S}",
            initialDelayString = "${opencrm.erp-sync.initial-delay:PT30S}")
    public void syncQueued() {
        Instant now = clock.instant();
        Integer requeued = tx.execute(status -> repository.requeueStaleErpSyncs(now.minus(staleAfter), now));
        if (requeued != null && requeued > 0) {
            log.warn("Requeued {} ERP syncs left IN_PROGRESS for more than {}", requeued, staleAfter);
        }
        List<Long> queued = tx.execute(status ->
                repository.findIdsByErpSyncStatus(ErpSyncStatus.QUEUED, PageRequest.of(0, batchSize)));
        if (queued != null) {
            queued.forEach(this::syncOne);
        }
    }

    void syncOne(Long id) {
        if (!transition(id, ErpSyncStatus.QUEUED, ErpSyncStatus.IN_PROGRESS)) {
            return; // another replica claimed it
        }
        Subscription subscription = tx.execute(status -> repository.findById(id).orElse(null));
        if (subscription == null) {
            return;
        }
        String erpSubscriptionId;
        try {
            erpSubscriptionId = erpAdapter.syncToErp(subscription);
        } catch (ErpRateLimitException e) {
            log.info("ERP rate limit reached; subscription {} stays queued", subscription.getSubscriptionNumber());
            transition(id, ErpSyncStatus.IN_PROGRESS, ErpSyncStatus.QUEUED);
            return;
        } catch (RuntimeException e) {
            log.error("ERP sync failed for subscription {}", subscription.getSubscriptionNumber(), e);
            transition(id, ErpSyncStatus.IN_PROGRESS, ErpSyncStatus.FAILED);
            return;
        }
        tx.executeWithoutResult(status -> {
            Subscription fresh = repository.findById(id).orElseThrow();
            fresh.setErpSubscriptionId(erpSubscriptionId);
            fresh.setErpSyncStatus(ErpSyncStatus.SYNCED);
            if (fresh.getStatus() == SubscriptionStatus.PENDING) {
                fresh.activate();
            }
            repository.save(fresh);
        });
        log.info("Synced subscription {} to ERP ({}) and activated it", subscription.getSubscriptionNumber(),
                erpSubscriptionId);
    }

    private boolean transition(Long id, String from, String to) {
        Integer changed = tx.execute(status -> repository.transitionErpSyncStatus(id, from, to, clock.instant()));
        return changed != null && changed == 1;
    }
}
