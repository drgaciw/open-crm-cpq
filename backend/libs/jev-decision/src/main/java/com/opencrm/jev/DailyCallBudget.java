package com.opencrm.jev;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Per-instance ceiling on upstream requests per UTC day. Upstream pricing and rate limits are
 * unpublished (ADR-015), so spend is bounded locally rather than trusted to the provider.
 */
final class DailyCallBudget {

    private final int limit;
    private final Clock clock;
    private LocalDate day;
    private int used;

    DailyCallBudget(int limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
        this.day = today();
    }

    /** @return true and consumes one call, or false when today is exhausted */
    synchronized boolean tryAcquire() {
        LocalDate now = today();
        if (!now.equals(day)) {
            day = now;
            used = 0;
        }
        if (used >= limit) {
            return false;
        }
        used++;
        return true;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
