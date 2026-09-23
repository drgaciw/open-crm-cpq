package com.opencrm.crm.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * {@code opencrm.crm.quote.accepted{channel=redis|kafka, outcome=...}}: every quote acceptance crm-service receives,
 * by channel and {@link QuoteAcceptedOutcome} (ADR-018 D8).
 *
 * <p>This is the evidence for ending the dual run. While both channels are live, every acceptance should arrive on
 * both: one {@code applied} and one {@code duplicate} between them. Once Kafka's count matches Redis's over the agreed
 * window, with no dead letters, the Redis channel can go (D8 step 3). See {@code docs/runbooks/event-operations.md}.
 */
@Component
public class QuoteAcceptedChannelMetrics {

    /** Meter name. */
    public static final String METER = "opencrm.crm.quote.accepted";

    public static final String REDIS = "redis";
    public static final String KAFKA = "kafka";

    private final ObjectProvider<MeterRegistry> meterRegistry;

    public QuoteAcceptedChannelMetrics(ObjectProvider<MeterRegistry> meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String channel, QuoteAcceptedOutcome outcome) {
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry == null || outcome == null) {
            return;
        }
        Counter.builder(METER)
                .description("Quote acceptances received by crm-service, by channel and outcome")
                .tag("channel", channel)
                .tag("outcome", outcome.tag())
                .register(registry)
                .increment();
    }
}
