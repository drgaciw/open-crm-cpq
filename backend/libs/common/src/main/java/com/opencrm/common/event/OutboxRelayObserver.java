package com.opencrm.common.event;

/**
 * Callbacks from {@link OutboxRelayPublisher} for metrics (ADR-018 D10). Kept free of Micrometer types so the relay
 * does not need Micrometer on the classpath.
 */
public interface OutboxRelayObserver {

    OutboxRelayObserver NOOP = new OutboxRelayObserver() {
    };

    /** The broker acknowledged {@code event} and it was marked published. */
    default void published(OutboxEvent event) {
    }

    /** A publish attempt failed; {@code exhausted} means the row is now {@link OutboxStatus#FAILED}. */
    default void attemptFailed(OutboxEvent event, boolean exhausted) {
    }
}
