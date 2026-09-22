package com.opencrm.common.event;

/**
 * Relay state of an {@link OutboxEvent} row (ADR-018 D2).
 * <ul>
 *   <li>{@link #PENDING}: not yet acknowledged by the broker. Eligible for the next lease once
 *       {@code next_attempt_at} has passed.</li>
 *   <li>{@link #PUBLISHED}: the broker acknowledged the record. Removed after the retention period.</li>
 *   <li>{@link #FAILED}: the retry limit was reached. The row is kept, it blocks later rows of the same aggregate
 *       (per-aggregate order), and the {@code outbox} health indicator reports DOWN until someone requeues it.</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
