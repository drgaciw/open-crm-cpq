package com.opencrm.ai.task;

import java.time.Duration;

/**
 * Hard ceilings for claim batches, lease length, and retry backoff.
 * Configuration may choose a smaller value. It cannot raise these.
 */
public final class AgentTaskLimits {

    public static final int MAX_CLAIM_LIMIT = 100;
    public static final int DEFAULT_CLAIM_LIMIT = 10;
    public static final int MAX_ERROR_LENGTH = 500;
    public static final int MAX_BACKOFF_MULTIPLIER = 8;
    public static final Duration MAX_BACKOFF = Duration.ofMinutes(15);
    public static final Duration MAX_LEASE = Duration.ofMinutes(2);

    private AgentTaskLimits() {
    }

    public static int clampClaimLimit(int requested) {
        if (requested < 1) {
            throw new IllegalArgumentException("claim limit must be positive");
        }
        return Math.min(requested, MAX_CLAIM_LIMIT);
    }

    public static Duration clampLease(Duration requested) {
        if (requested == null || requested.isZero() || requested.isNegative()) {
            throw new IllegalArgumentException("lease must be positive");
        }
        return requested.compareTo(MAX_LEASE) > 0 ? MAX_LEASE : requested;
    }

    public static Duration backoff(int attemptsAfterFailure, Duration initial, int multiplier, Duration configuredCap) {
        if (attemptsAfterFailure < 1) {
            throw new IllegalArgumentException("attemptsAfterFailure must be positive");
        }
        Duration cap = configuredCap == null || configuredCap.isZero() || configuredCap.isNegative()
                || configuredCap.compareTo(MAX_BACKOFF) > 0
                ? MAX_BACKOFF : configuredCap;
        int factor = multiplier < 2 ? 2 : Math.min(multiplier, MAX_BACKOFF_MULTIPLIER);
        Duration delay = initial == null || initial.isZero() || initial.isNegative()
                ? Duration.ofSeconds(1) : initial;
        if (delay.compareTo(cap) > 0) {
            delay = cap;
        }
        for (int step = 1; step < attemptsAfterFailure; step++) {
            if (delay.compareTo(cap.dividedBy(factor)) >= 0) {
                return cap;
            }
            delay = delay.multipliedBy(factor);
            if (delay.compareTo(cap) > 0) {
                return cap;
            }
        }
        return delay;
    }

    public static String truncateError(String error) {
        if (error == null || error.isBlank()) {
            return "failed";
        }
        if (error.length() <= MAX_ERROR_LENGTH) {
            return error;
        }
        return error.substring(0, MAX_ERROR_LENGTH);
    }
}
