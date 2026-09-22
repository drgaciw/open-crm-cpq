package com.opencrm.jev.transport;

import com.opencrm.jev.error.JevException;

import java.util.function.DoubleSupplier;

/**
 * Bounded retry for transient failures. A server supplied Retry-After wins; otherwise exponential
 * backoff with jitter. Advisory calls are cheap to drop, so the ceiling is deliberately low.
 */
public final class RetryPolicy {

    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final DoubleSupplier jitter;

    public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs) {
        this(maxRetries, baseDelayMs, maxDelayMs, Math::random);
    }

    /** @param jitter supplies values in [0,1); injectable so tests are deterministic */
    public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs, DoubleSupplier jitter) {
        this.maxRetries = Math.max(0, maxRetries);
        this.baseDelayMs = Math.max(0, baseDelayMs);
        this.maxDelayMs = Math.max(this.baseDelayMs, maxDelayMs);
        this.jitter = jitter;
    }

    public int maxRetries() {
        return maxRetries;
    }

    /** @param attempt zero-based index of the attempt that just failed */
    public boolean shouldRetry(JevException failure, int attempt) {
        return failure.retryable() && attempt < maxRetries;
    }

    /** @param attempt zero-based index of the attempt that just failed */
    public long delayMs(JevException failure, int attempt) {
        if (failure instanceof JevException.RateLimit limited && limited.retryAfterSeconds() != null) {
            return Math.min(maxDelayMs, Math.max(0, limited.retryAfterSeconds()) * 1000);
        }
        long backoff = baseDelayMs * (1L << Math.min(attempt, 20));
        long jittered = backoff + (long) (jitter.getAsDouble() * baseDelayMs / 2);
        return Math.min(maxDelayMs, jittered);
    }
}
