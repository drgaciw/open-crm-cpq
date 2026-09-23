package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/**
 * Kafka record headers of the event envelope (ADR-018 D4). The outbox relay writes all of them from the outbox row and
 * {@link EventContractRegistry}; a header whose value is unknown is left out. Values are UTF-8 strings. Payloads are
 * unchanged, so a consumer that ignores headers is unaffected.
 */
public final class EventHeaders {

    /** The outbox row UUID: a stable id for the event, identical on every re-send. The consumer dedupe key (D6). */
    public static final String EVENT_ID = "eventId";

    /** When the event was recorded in the outbox (ISO-8601 instant). */
    public static final String OCCURRED_AT = "occurredAt";

    /** {@code spring.application.name} of the producing service. */
    public static final String PRODUCER = "producer";

    /** Registry event type, e.g. {@code QuoteAccepted}. */
    public static final String EVENT_TYPE = "eventType";

    /** Registry contract version, e.g. {@code 1}. */
    public static final String EVENT_VERSION = "eventVersion";

    /** The chain the event belongs to; the event's own {@code eventId} when it starts one. Always present. */
    public static final String CORRELATION_ID = "correlationId";

    /** The {@code eventId} of the event that caused this one; absent when the event was not caused by another. */
    public static final String CAUSATION_ID = "causationId";

    /** Who caused the event: {@code user:<sub>} or {@code service:<name>} (ADR-016). */
    public static final String ACTOR = "actor";

    private EventHeaders() {
    }

    /** The last value of {@code name} on {@code record}, or {@code null} if absent or empty. */
    public static String value(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null || header.value() == null || header.value().length == 0) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
