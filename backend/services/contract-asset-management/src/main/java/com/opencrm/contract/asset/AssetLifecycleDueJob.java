package com.opencrm.contract.asset;

import com.opencrm.contract.documents.apply.AssetLifecycleReadiness;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

/**
 * UTC due sweep. The tick returns immediately while the lifecycle hold is on, so production
 * stays inert without a second flag.
 */
@Component
public class AssetLifecycleDueJob {

    public static final String OVERDUE_GAUGE = "opencrm.asset.lifecycle.overdue.seconds";

    private final AssetLifecycleReadiness readiness;
    private final AssetLifecycleDueRunner runner;
    private final AssetLifecycleChangeRepository changes;
    private final Clock clock;
    private final AtomicLong overdueSeconds;

    @Autowired
    public AssetLifecycleDueJob(AssetLifecycleReadiness readiness, AssetLifecycleDueRunner runner,
                                AssetLifecycleChangeRepository changes, MeterRegistry meters) {
        this(readiness, runner, changes, Clock.systemUTC(), meters);
    }

    AssetLifecycleDueJob(AssetLifecycleReadiness readiness, AssetLifecycleDueRunner runner,
                         AssetLifecycleChangeRepository changes, Clock clock, MeterRegistry meters) {
        this.readiness = readiness;
        this.runner = runner;
        this.changes = changes;
        this.clock = clock;
        AtomicLong gauge = meters.gauge(OVERDUE_GAUGE, new AtomicLong());
        this.overdueSeconds = gauge == null ? new AtomicLong() : gauge;
    }

    @Scheduled(fixedDelayString = "${opencrm.asset.lifecycle.poll-interval:PT30S}",
            initialDelayString = "${opencrm.asset.lifecycle.initial-delay:PT30S}")
    public void tick() {
        if (!readiness.isReady()) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        updateOverdue(today);
        runner.runDue();
        updateOverdue(today);
    }

    private void updateOverdue(LocalDate today) {
        LocalDate oldest = changes.findOldestDueDate(today);
        overdueSeconds.set(oldest == null ? 0 : Math.max(0,
                Duration.between(oldest.atStartOfDay(ZoneOffset.UTC).toInstant(), clock.instant()).getSeconds()));
    }
}
