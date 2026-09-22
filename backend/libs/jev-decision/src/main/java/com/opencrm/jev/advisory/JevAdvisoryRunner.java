package com.opencrm.jev.advisory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * Runs advisory work off the request path (ADR-015). Work is deferred until the surrounding transaction
 * commits, runs on its own virtual thread, is dropped when the runner is saturated, and can never
 * propagate a failure to the caller. A rolled-back transaction schedules nothing.
 */
public class JevAdvisoryRunner implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JevAdvisoryRunner.class);

    private final ExecutorService executor;
    private final Semaphore permits;

    public JevAdvisoryRunner(int maxConcurrent) {
        this(maxConcurrent, Executors.newVirtualThreadPerTaskExecutor());
    }

    /** @param executor injectable so tests can run tasks inline */
    public JevAdvisoryRunner(int maxConcurrent, ExecutorService executor) {
        this.permits = new Semaphore(Math.max(1, maxConcurrent));
        this.executor = executor;
    }

    /**
     * Schedules the task after commit when a transaction is active, immediately otherwise. Never throws.
     */
    public void submitAfterCommit(String useCase, Runnable task) {
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submit(useCase, task);
                    }
                });
            } else {
                submit(useCase, task);
            }
        } catch (Throwable e) {
            log.warn("Could not schedule Jev {} evaluation: {}", useCase, e.getMessage());
        }
    }

    private void submit(String useCase, Runnable task) {
        if (!permits.tryAcquire()) {
            log.debug("Jev advisory runner saturated; dropping {} evaluation", useCase);
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    log.warn("Jev {} evaluation failed: {}", useCase, e.getMessage());
                } finally {
                    permits.release();
                }
            });
        } catch (RejectedExecutionException e) {
            permits.release();
            log.debug("Jev advisory runner is shut down; dropping {} evaluation", useCase);
        } catch (Throwable e) {
            permits.release();
            log.warn("Could not start Jev {} evaluation: {}", useCase, e.getMessage());
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
