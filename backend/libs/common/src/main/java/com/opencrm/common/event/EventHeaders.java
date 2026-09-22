package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;

/**
 * Kafka record headers of the event envelope (ADR-018 D4). The outbox relay sets the first three from the outbox row;
 * the rest of the D4 envelope ({@code eventType}, {@code eventVersion}, {@code correlationId}, {@code causationId},
 * {@code actor}) needs registry and outbox columns and is not written yet. Values are UTF-8 strings.
 */
public final class EventHeaders {

    /** The outbox row UUID: a stable id for the event, identical on every re-send. The consumer dedupe key (D6). */
    public static final String EVENT_ID = "eventId";

    /** When the event was recorded in the outbox (ISO-8601 instant). */
    public static final String OCCURRED_AT = "occurredAt";

    /** {@code spring.application.name} of the producing service. */
    public static final String PRODUCER = "producer";

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
