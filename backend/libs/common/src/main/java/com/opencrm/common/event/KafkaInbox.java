package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Consumer inbox (ADR-018 D6): remembers which events a consumer group has already processed in this schema, so a
 * redelivered or re-sent event is recognised and skipped.
 *
 * <p>Call {@link #claim} first in the listener, <b>inside the listener's database transaction</b>. The claim row is
 * written by the same transaction as the side effect: if the listener fails, both roll back and the redelivery is
 * processed again; if it commits, a later copy of the event is refused.
 *
 * <p>The dedupe key is the {@link EventHeaders#EVENT_ID} header (the outbox row UUID), which survives a relay re-send
 * after an ack timeout. A record without it (a producer that does not go through the outbox relay) falls back to its
 * delivery coordinates {@code topic-partition@offset}: that still catches consumer-side redelivery (rebalance, retry),
 * but not a producer-side duplicate, so such consumers also keep a natural-key check.
 *
 * <p>Uses plain JDBC on the service's own {@code processed_events} table (created by that service's Flyway migration),
 * not a JPA entity: every service scans {@code com.opencrm.common} for entities with {@code ddl-auto: validate}, and
 * only consuming schemas have the table. Registered by {@link KafkaInboxAutoConfiguration} when
 * {@code opencrm.kafka.inbox.enabled=true}.
 */
public class KafkaInbox {

    private final JdbcTemplate jdbc;

    public KafkaInbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records {@code record} as processed by {@code consumerGroup}.
     *
     * @return {@code true} the first time, {@code false} if this event was already processed by this group
     * @throws IllegalStateException if no transaction is active (the claim would not be atomic with the side effect)
     */
    public boolean claim(String consumerGroup, ConsumerRecord<?, ?> record) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("KafkaInbox.claim must run inside the listener's transaction");
        }
        int inserted = jdbc.update(
                "INSERT INTO processed_events (consumer_group, event_id, processed_at) VALUES (?, ?, now()) "
                        + "ON CONFLICT (consumer_group, event_id) DO NOTHING",
                consumerGroup, eventIdOf(record));
        return inserted == 1;
    }

    /** Deletes claims older than {@code before}; returns the number removed. */
    public int purgeProcessedBefore(Instant before) {
        return jdbc.update("DELETE FROM processed_events WHERE processed_at < ?", Timestamp.from(before));
    }

    /** The dedupe key for {@code record}: its {@code eventId} header, else {@code topic-partition@offset}. */
    public static String eventIdOf(ConsumerRecord<?, ?> record) {
        String eventId = EventHeaders.value(record, EventHeaders.EVENT_ID);
        if (eventId != null) {
            return eventId;
        }
        return record.topic() + "-" + record.partition() + "@" + record.offset();
    }
}
