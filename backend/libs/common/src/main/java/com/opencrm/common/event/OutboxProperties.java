package com.opencrm.common.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code opencrm.outbox.*} settings for the transactional-outbox relay and its health and metrics (ADR-018 D1–D3,
 * D10). Every default is safe for local development and CPQ volumes.
 */
@Getter
@ConfigurationProperties(prefix = "opencrm.outbox")
public class OutboxProperties {

    private final Relay relay = new Relay();
    private final Health health = new Health();

    @Getter
    @Setter
    public static class Relay {

        /** Opt-in switch (D1). Enabling it replays whatever backlog the schema holds; triage that first. */
        private boolean enabled = false;

        /** Delay between relay ticks. Read by {@code @Scheduled}; use ISO-8601, e.g. {@code PT5S}. */
        private Duration pollInterval = Duration.ofSeconds(5);

        /** Maximum rows leased per transaction (D3). */
        private int batchSize = 100;

        /**
         * How long one lease waits for broker acknowledgements (D2). Also pinned as the producer's
         * {@code max.block.ms}, so an unreachable broker cannot hold the lease longer than this.
         */
        private Duration ackTimeout = Duration.ofSeconds(10);

        /** Attempts before a row becomes {@link OutboxStatus#FAILED} (D2). */
        private int maxAttempts = 10;

        /** First retry delay; doubles per attempt up to {@link #maxBackoff}. */
        private Duration initialBackoff = Duration.ofSeconds(5);

        private Duration maxBackoff = Duration.ofMinutes(5);

        /**
         * Leases per tick while each one makes progress. Only one row per aggregate is leased at a time, so this is
         * what drains a burst of events for one aggregate within a single tick.
         */
        private int maxDrainIterations = 10;

        /** Published rows older than this are deleted by the cleanup job. */
        private Duration retention = Duration.ofDays(7);

        /** Cleanup schedule. Read by {@code @Scheduled}. */
        private String cleanupCron = "0 0 3 * * *";
    }

    @Getter
    @Setter
    public static class Health {

        /** With the relay running, a pending row older than this turns the {@code outbox} indicator DOWN. */
        private Duration maxPendingAge = Duration.ofMinutes(10);

        /** How long a backlog snapshot is reused by health checks and metric scrapes. */
        private Duration snapshotTtl = Duration.ofSeconds(10);
    }
}
