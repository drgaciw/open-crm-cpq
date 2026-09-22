package com.opencrm.common.event;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * The {@code outbox} health indicator (ADR-018 D1, D2).
 * <ul>
 *   <li><b>Relay running:</b> DOWN when any row is {@link OutboxStatus#FAILED}, or when the oldest pending row is
 *       older than {@code opencrm.outbox.health.max-pending-age}; otherwise UP.</li>
 *   <li><b>Relay not running</b> ({@code opencrm.outbox.relay.enabled=false}, the default): UNKNOWN, with the
 *       backlog in the details. The ADR asks for DOWN here, but the relay is off everywhere until each environment
 *       has triaged its backlog, and every compose healthcheck reads {@code /actuator/health}; DOWN would mark every
 *       producer container unhealthy. UNKNOWN never lowers the aggregate status, so the backlog stays visible
 *       without breaking probes. Revisit once the relay is on by default.</li>
 * </ul>
 */
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxBacklogProbe probe;
    private final BooleanSupplier relayRunning;
    private final Duration maxPendingAge;

    public OutboxHealthIndicator(OutboxBacklogProbe probe, BooleanSupplier relayRunning, Duration maxPendingAge) {
        this.probe = probe;
        this.relayRunning = relayRunning;
        this.maxPendingAge = maxPendingAge;
    }

    @Override
    public Health health() {
        OutboxBacklogProbe.Snapshot backlog = probe.snapshot();
        boolean running = relayRunning.getAsBoolean();
        Health.Builder builder;
        if (!running) {
            builder = Health.unknown();
        } else if (backlog.failed() > 0) {
            builder = Health.down().withDetail("reason",
                    backlog.failed() + " row(s) FAILED after the retry limit; fix the cause, then requeue them");
        } else if (backlog.oldestPendingAge().compareTo(maxPendingAge) > 0) {
            builder = Health.down().withDetail("reason",
                    "oldest pending row is older than " + maxPendingAge + "; the broker may be unreachable");
        } else {
            builder = Health.up();
        }
        return builder
                .withDetail("relay", running ? "running" : "disabled (opencrm.outbox.relay.enabled=false)")
                .withDetail("pending", backlog.pending())
                .withDetail("failed", backlog.failed())
                .withDetail("oldestPendingAge", backlog.oldestPendingAge().toString())
                .build();
    }
}
