package com.opencrm.common.event;

import java.util.Optional;

/**
 * The correlation and causation of the events published on this thread (ADR-018 D4).
 *
 * <p>{@link OutboxEventPublisher} writes the active context into each outbox row, and the relay sends it as the
 * {@code correlationId} and {@code causationId} headers. With no active context, an event starts its own chain: its
 * correlation id is its own event id and it has no causation id.
 *
 * <p>Contexts are opened in two places:
 * <ul>
 *   <li>{@link EventCorrelationRecordInterceptor}, around every Kafka listener call. An event published while handling
 *       a record continues that record's chain, and the record's {@code eventId} becomes its causation id.</li>
 *   <li>A producer that already has a business correlation, such as a quote's UUID, opens one around its publish so
 *       the headers agree with the payload.</li>
 * </ul>
 *
 * <pre>{@code
 * try (EventCorrelation.Scope ignored = EventCorrelation.begin(correlationId, causationId)) {
 *     outboxEventPublisher.publish(...);
 * }
 * }</pre>
 */
public final class EventCorrelation {

    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

    private EventCorrelation() {
    }

    /** One correlation context. Either id may be {@code null}. */
    public record Context(String correlationId, String causationId) {
    }

    /** The context active on this thread, if any. */
    public static Optional<Context> current() {
        return Optional.ofNullable(CURRENT.get());
    }

    /**
     * Makes {@code correlationId} and {@code causationId} current until the returned scope is closed; closing it
     * restores whatever was current before, so scopes nest.
     */
    public static Scope begin(String correlationId, String causationId) {
        Context previous = CURRENT.get();
        CURRENT.set(new Context(blankToNull(correlationId), blankToNull(causationId)));
        return new Scope(previous);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Restores the previous context when closed. Close it on the thread that opened it. */
    public static final class Scope implements AutoCloseable {

        private final Context previous;
        private boolean closed;

        private Scope(Context previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }
}
