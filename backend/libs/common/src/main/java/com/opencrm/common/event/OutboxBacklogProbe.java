package com.opencrm.common.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Reads the outbox backlog for the health indicator and metrics (ADR-018 D1, D10). Three indexed aggregate queries,
 * cached for {@code opencrm.outbox.health.snapshot-ttl} so health checks and metric scrapes cannot hammer the
 * database.
 */
public class OutboxBacklogProbe {

    private final OutboxEventRepository repository;
    private final Duration ttl;
    private final Clock clock;
    private Snapshot cached;

    public OutboxBacklogProbe(OutboxEventRepository repository, Duration ttl, Clock clock) {
        this.repository = repository;
        this.ttl = ttl;
        this.clock = clock;
    }

    public synchronized Snapshot snapshot() {
        Instant now = clock.instant();
        if (cached == null || !now.isBefore(cached.takenAt().plus(ttl))) {
            cached = read(now);
        }
        return cached;
    }

    private Snapshot read(Instant now) {
        long pending = repository.countByStatus(OutboxStatus.PENDING);
        long failed = repository.countByStatus(OutboxStatus.FAILED);
        Instant oldest = pending == 0 ? null : repository.findOldestCreatedAt(OutboxStatus.PENDING);
        Duration oldestAge = oldest == null ? Duration.ZERO : Duration.between(oldest, now);
        return new Snapshot(pending, failed, oldestAge.isNegative() ? Duration.ZERO : oldestAge, now);
    }

    /** Backlog at {@code takenAt}; {@code oldestPendingAge} is zero when nothing is pending. */
    public record Snapshot(long pending, long failed, Duration oldestPendingAge, Instant takenAt) {
    }
}
