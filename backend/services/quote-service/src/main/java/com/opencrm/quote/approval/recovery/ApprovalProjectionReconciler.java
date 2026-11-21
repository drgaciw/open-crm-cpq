package com.opencrm.quote.approval.recovery;

import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.quote.Quote;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable candidates are quote rows, not in-memory tasks. A restart begins another sweep. */
@Component
@Slf4j
public class ApprovalProjectionReconciler {
    private final ApprovalProjectionRepository candidates;
    private final ApprovalRoutingClient routing;
    private final ApprovalProjectionWriter writer;
    private final io.micrometer.core.instrument.MeterRegistry meters;
    private final java.util.concurrent.Executor executor;
    private final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();
    private long afterId;

    public ApprovalProjectionReconciler(ApprovalProjectionRepository candidates, ApprovalRoutingClient routing,
            ApprovalProjectionWriter writer, io.micrometer.core.instrument.MeterRegistry meters,
            @org.springframework.beans.factory.annotation.Qualifier("approvalProjectionExecutor") java.util.concurrent.Executor executor) {
        this.candidates = candidates;
        this.routing = routing;
        this.writer = writer;
        this.meters = meters;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${approvals.projection-reconcile-delay-ms:30000}",
            initialDelayString = "${approvals.projection-reconcile-delay-ms:30000}")
    public void tick() {
        // At most one task is running/queued; the shared scheduler never performs network I/O.
        if (!running.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try { reconcile(); }
                catch (RuntimeException ex) {
                    meters.counter("approval.projection.reconcile", "outcome", "SCAN_FAILURE").increment();
                    log.warn("Approval projection scan deferred ({})", ex.getClass().getSimpleName());
                } finally { running.set(false); }
            });
        } catch (RuntimeException ex) { running.set(false); throw ex; }
    }

    public synchronized void reconcile() {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        var batch = candidates.pendingAfter(afterId, PageRequest.of(0, 20));
        if (batch.isEmpty()) { afterId = 0; return; }
        for (Quote quote : batch) {
            // A single in-flight GET still has the existing E3 30-second read bound. Do not start another
            // once this tick's budget expires, and never monopolize the outbox/shared scheduler.
            if (System.nanoTime() >= deadline) break;
            afterId = quote.getId();
            try {
                var outcome = writer.reconcile(quote.getId(), quote.getVersion(), quote.getApprovalSubmissionId(),
                        routing.getById(quote.getApprovalSubmissionId()));
                meters.counter("approval.projection.reconcile", "outcome", outcome.name()).increment();
                if (outcome != ApprovalProjectionWriter.Outcome.APPLIED && outcome != ApprovalProjectionWriter.Outcome.PENDING
                        && outcome != ApprovalProjectionWriter.Outcome.ALREADY_FINAL) {
                    log.warn("Approval projection blocked: quote={} submission={} outcome={}",
                            quote.getId(), quote.getApprovalSubmissionId(), outcome);
                }
            } catch (RuntimeException ex) {
                // Keep the durable candidate eligible for a later sweep, including on auth/transport/lock failure.
                meters.counter("approval.projection.reconcile", "outcome", "RETRYABLE_FAILURE").increment();
                log.warn("Approval projection reconciliation deferred for quote {} ({})", quote.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
