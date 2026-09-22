package com.opencrm.common.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Outbox metrics (ADR-018 D10). Gauges read the cached {@link OutboxBacklogProbe}; counters are fed by the relay
 * through {@link OutboxRelayObserver}.
 * <ul>
 *   <li>{@code opencrm.outbox.pending}, {@code opencrm.outbox.failed}: row counts by status.</li>
 *   <li>{@code opencrm.outbox.oldest.pending.age} (seconds): how far the relay is behind.</li>
 *   <li>{@code opencrm.outbox.relay.attempts{result=published|failed|exhausted}}: relay outcomes.</li>
 * </ul>
 * Consumer lag and DLT depth arrive with D7.
 */
public class OutboxMetrics implements MeterBinder, OutboxRelayObserver {

    private final OutboxBacklogProbe probe;
    private volatile Counter published;
    private volatile Counter failed;
    private volatile Counter exhausted;

    public OutboxMetrics(OutboxBacklogProbe probe) {
        this.probe = probe;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("opencrm.outbox.pending", probe, p -> p.snapshot().pending())
                .description("Outbox rows waiting for a broker acknowledgement")
                .register(registry);
        Gauge.builder("opencrm.outbox.failed", probe, p -> p.snapshot().failed())
                .description("Outbox rows that reached the retry limit")
                .register(registry);
        Gauge.builder("opencrm.outbox.oldest.pending.age", probe, p -> p.snapshot().oldestPendingAge().toMillis() / 1000.0)
                .description("Age of the oldest pending outbox row")
                .baseUnit("seconds")
                .register(registry);
        published = attempts(registry, "published");
        failed = attempts(registry, "failed");
        exhausted = attempts(registry, "exhausted");
    }

    private static Counter attempts(MeterRegistry registry, String result) {
        return Counter.builder("opencrm.outbox.relay.attempts")
                .description("Outbox relay publish attempts by outcome")
                .tag("result", result)
                .register(registry);
    }

    @Override
    public void published(OutboxEvent event) {
        increment(published);
    }

    @Override
    public void attemptFailed(OutboxEvent event, boolean exhaustedNow) {
        increment(failed);
        if (exhaustedNow) {
            increment(exhausted);
        }
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }
}
