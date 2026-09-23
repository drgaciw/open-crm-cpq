package com.opencrm.common.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

/**
 * Counts records sent to a dead-letter topic (ADR-018 D10): {@code opencrm.kafka.consumer.dead.letters}, tagged with
 * the source {@code topic}, the consumer {@code group} and the {@code exception} (the listener's own exception, not
 * Spring Kafka's wrapper). The count only moves once {@code delegate} has published the record: if the DLT publish
 * fails, the delegate throws, nothing is counted, and the error handler tries again.
 */
public class DeadLetterCountingRecoverer implements ConsumerAwareRecordRecoverer {

    /** Meter name of the dead-letter counter. */
    public static final String METER = "opencrm.kafka.consumer.dead.letters";

    private final ConsumerAwareRecordRecoverer delegate;
    private final ObjectProvider<MeterRegistry> meterRegistry;

    public DeadLetterCountingRecoverer(ConsumerAwareRecordRecoverer delegate,
                                       ObjectProvider<MeterRegistry> meterRegistry) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void accept(ConsumerRecord<?, ?> record, Consumer<?, ?> consumer, Exception exception) {
        delegate.accept(record, consumer, exception);
        MeterRegistry registry = meterRegistry.getIfAvailable();
        if (registry != null) {
            Counter.builder(METER)
                    .description("Records published to a dead-letter topic after failing in a listener")
                    .tag("topic", record.topic())
                    .tag("group", group(consumer))
                    .tag("exception", cause(exception).getClass().getSimpleName())
                    .register(registry)
                    .increment();
        }
    }

    private static String group(Consumer<?, ?> consumer) {
        try {
            return consumer == null ? "unknown" : consumer.groupMetadata().groupId();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static Throwable cause(Exception exception) {
        Throwable cause = exception;
        while (cause instanceof ListenerExecutionFailedException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause == null ? exception : cause;
    }
}
