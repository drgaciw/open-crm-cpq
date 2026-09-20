package com.opencrm.jev.advisory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("JevAdvisoryRunner")
class JevAdvisoryRunnerTest {

    /** Runs tasks on the calling thread, or parks them when held. */
    private static final class ControlledExecutor extends AbstractExecutorService {
        private final List<Runnable> parked = new ArrayList<>();
        private boolean hold;
        private boolean shutdown;

        @Override
        public void execute(Runnable command) {
            if (hold) {
                parked.add(command);
            } else {
                command.run();
            }
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }

    private final ControlledExecutor executor = new ControlledExecutor();
    private final AtomicInteger ran = new AtomicInteger();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("runs immediately when there is no transaction")
    void runsWithoutTransaction() {
        new JevAdvisoryRunner(2, executor).submitAfterCommit("lead-score", ran::incrementAndGet);

        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("waits for commit, and a rollback schedules nothing")
    void defersUntilCommit() {
        TransactionSynchronizationManager.initSynchronization();
        new JevAdvisoryRunner(2, executor).submitAfterCommit("lead-score", ran::incrementAndGet);

        assertThat(ran).hasValue(0);
        List<TransactionSynchronization> registered = TransactionSynchronizationManager.getSynchronizations();
        assertThat(registered).hasSize(1);

        registered.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(ran).hasValue(0);

        registered.get(0).afterCommit();
        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("a throwing task never reaches the caller and frees its permit")
    void swallowsFailures() {
        JevAdvisoryRunner runner = new JevAdvisoryRunner(1, executor);

        assertThatCode(() -> runner.submitAfterCommit("lead-score", () -> {
            throw new IllegalStateException("boom");
        })).doesNotThrowAnyException();

        runner.submitAfterCommit("lead-score", ran::incrementAndGet);
        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("drops work when saturated instead of queueing it")
    void dropsWhenSaturated() {
        executor.hold = true;
        JevAdvisoryRunner runner = new JevAdvisoryRunner(1, executor);

        runner.submitAfterCommit("lead-score", ran::incrementAndGet);
        runner.submitAfterCommit("lead-score", ran::incrementAndGet);

        assertThat(executor.parked).hasSize(1);
        executor.parked.forEach(Runnable::run);
        assertThat(ran).hasValue(1);
    }

    @Test
    @DisplayName("submitting after close is ignored")
    void ignoresSubmissionsAfterClose() {
        JevAdvisoryRunner runner = new JevAdvisoryRunner(1);
        runner.close();

        assertThatCode(() -> runner.submitAfterCommit("lead-score", ran::incrementAndGet))
                .doesNotThrowAnyException();
    }
}
