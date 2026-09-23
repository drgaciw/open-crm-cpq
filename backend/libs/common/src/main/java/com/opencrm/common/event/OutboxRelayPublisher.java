package com.opencrm.common.event;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Outbox relay: leases due {@code outbox_events} rows and forwards them to Kafka (ADR-010, ADR-018 D1–D3).
 *
 * <p>Delivery is at-least-once, and it is now actually true:
 * <ul>
 *   <li><b>D2, acknowledge before marking.</b> A row is marked published only after the broker acknowledged its
 *       record within {@code ack-timeout}. A failed or unacknowledged send leaves the row unpublished, records the
 *       error, and backs off exponentially. After {@code max-attempts} the row becomes {@link OutboxStatus#FAILED},
 *       which the {@code outbox} health indicator reports.</li>
 *   <li><b>D3, leasing.</b> Each lease is one transaction around {@link OutboxEventRepository#leaseDueBatch}
 *       ({@code FOR UPDATE SKIP LOCKED}), so replicas never publish the same row. Only the oldest unpublished row of
 *       each aggregate is leased, so per-aggregate order holds even when a send fails.</li>
 * </ul>
 * Records within one lease belong to different aggregates, so they are sent together and awaited against one
 * shared deadline; a lease holds its row locks for at most {@code ack-timeout}. A tick keeps leasing while leases
 * make progress, up to {@code max-drain-iterations}, which drains bursts for a single aggregate.
 *
 * <p>A record whose acknowledgement timed out may still reach the broker later and then be sent again: consumers
 * must deduplicate (ADR-018 D6).
 *
 * <p>Registered by {@link OutboxRelayAutoConfiguration}, not by component scanning (D1).
 */
@Slf4j
public class OutboxRelayPublisher {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final OutboxProperties.Relay properties;
    private final OutboxRelayObserver observer;
    private final Clock clock;
    private final String producerName;

    public OutboxRelayPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            TransactionTemplate transactionTemplate,
            OutboxProperties.Relay properties,
            OutboxRelayObserver observer,
            Clock clock,
            String producerName) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.observer = observer;
        this.clock = clock;
        this.producerName = producerName;
    }

    /** One relay tick. Never runs inside a caller's transaction: each lease commits on its own. */
    @Scheduled(fixedDelayString = "${opencrm.outbox.relay.poll-interval:PT5S}")
    public void publishPendingEvents() {
        int published = 0;
        int iterations = Math.max(1, properties.getMaxDrainIterations());
        for (int i = 0; i < iterations; i++) {
            LeaseResult result = transactionTemplate.execute(status -> relayLease());
            if (result == null) {
                break;
            }
            published += result.published();
            if (result.published() == 0) {
                break;
            }
        }
        if (published > 0) {
            log.info("Outbox relay published {} events", published);
        }
    }

    /**
     * Leases one batch, sends it, and records each outcome. Must run inside a transaction; the leased rows stay
     * locked until it commits.
     */
    LeaseResult relayLease() {
        Instant now = clock.instant();
        List<OutboxEvent> lease = repository.leaseDueBatch(now, properties.getBatchSize());
        if (lease.isEmpty()) {
            return LeaseResult.EMPTY;
        }

        int failed = 0;
        List<InFlight> inFlight = new ArrayList<>(lease.size());
        for (OutboxEvent event : lease) {
            try {
                inFlight.add(new InFlight(event, kafkaTemplate.send(toRecord(event))));
            } catch (RuntimeException e) {
                // A synchronous failure (no metadata within max.block.ms, serializer error) usually means the broker
                // is unreachable. Stop sending: the rest of the lease is left untouched and not counted as attempts.
                recordFailure(event, describe(e), now);
                failed++;
                break;
            }
        }

        int published = 0;
        long deadline = System.nanoTime() + properties.getAckTimeout().toNanos();
        for (InFlight sent : inFlight) {
            try {
                sent.ack().get(Math.max(0L, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                sent.event().markAsPublished(clock.instant());
                observer.published(sent.event());
                published++;
            } catch (TimeoutException e) {
                recordFailure(sent.event(), "No broker acknowledgement within " + properties.getAckTimeout(), now);
                failed++;
            } catch (ExecutionException e) {
                recordFailure(sent.event(), describe(e.getCause() != null ? e.getCause() : e), now);
                failed++;
            } catch (InterruptedException e) {
                // Unconfirmed rows stay PENDING without an attempt counted, and are sent again later.
                Thread.currentThread().interrupt();
                break;
            }
        }

        repository.saveAll(lease);
        if (failed > 0) {
            log.warn("Outbox relay lease: {} leased, {} published, {} failed", lease.size(), published, failed);
        }
        return new LeaseResult(lease.size(), published, failed);
    }

    /** Deletes published rows older than {@code retention} with one idempotent statement (D3). */
    @Scheduled(cron = "${opencrm.outbox.relay.cleanup-cron:0 0 3 * * *}")
    public void cleanupOldEvents() {
        Instant cutoff = clock.instant().minus(properties.getRetention());
        Integer deleted = transactionTemplate.execute(status -> repository.deletePublishedBefore(cutoff));
        if (deleted != null && deleted > 0) {
            log.info("Cleaned up {} published outbox events older than {}", deleted, cutoff);
        }
    }

    private void recordFailure(OutboxEvent event, String error, Instant now) {
        int attempt = event.getRetryCount() + 1;
        Duration delay = backoff(attempt, properties.getInitialBackoff(), properties.getMaxBackoff());
        boolean exhausted = event.recordFailedAttempt(error, now, properties.getMaxAttempts(), delay);
        observer.attemptFailed(event, exhausted);
        if (exhausted) {
            log.error("Outbox event {} ({} -> {}, aggregate {}) is FAILED after {} attempts: {}",
                    event.getId(), event.getAggregateType(), event.resolveTopic(), event.getAggregateId(),
                    attempt, error);
        } else {
            log.warn("Outbox event {} ({}) attempt {} failed, retry in {}: {}",
                    event.getId(), event.resolveTopic(), attempt, delay, error);
        }
    }

    /** {@code initial * 2^(attempt-1)}, capped at {@code max}. */
    static Duration backoff(int attempt, Duration initial, Duration max) {
        int exponent = Math.min(Math.max(attempt - 1, 0), 20);
        Duration delay = initial.multipliedBy(1L << exponent);
        return delay.compareTo(max) > 0 ? max : delay;
    }

    /**
     * The Kafka record for an outbox row (ADR-018 D4):
     * <ul>
     *   <li>topic: the {@code topic} column, or {@code event_type} on a row written before D4;</li>
     *   <li>key: {@code aggregate_id}, for per-aggregate partition order; payload: the JSON, unchanged;</li>
     *   <li>headers: {@code eventId} (the row UUID, identical on every re-send, the D6 dedupe key), {@code occurredAt},
     *       {@code producer}, {@code eventType} and {@code eventVersion} (from {@link EventContractRegistry}, which
     *       maps each topic to one contract), {@code correlationId} (the row's, or its {@code eventId} when it starts
     *       a chain), {@code causationId} and {@code actor}.</li>
     * </ul>
     */
    ProducerRecord<String, String> toRecord(OutboxEvent event) {
        String topic = event.resolveTopic();
        Optional<EventContractRegistry.EventContract> contract = EventContractRegistry.forTopic(topic);
        Integer version = event.getEventVersion() != null
                ? event.getEventVersion()
                : contract.map(EventContractRegistry.EventContract::eventVersion).orElse(null);

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getAggregateId(), event.getPayload());
        header(record, EventHeaders.EVENT_ID, event.getUuid());
        header(record, EventHeaders.OCCURRED_AT, event.getCreatedAt() == null ? null : event.getCreatedAt().toString());
        header(record, EventHeaders.PRODUCER, producerName);
        header(record, EventHeaders.EVENT_TYPE, contract.map(EventContractRegistry.EventContract::eventType).orElse(null));
        header(record, EventHeaders.EVENT_VERSION, version == null ? null : version.toString());
        header(record, EventHeaders.CORRELATION_ID,
                event.getCorrelationId() != null ? event.getCorrelationId() : event.getUuid());
        header(record, EventHeaders.CAUSATION_ID, event.getCausationId());
        header(record, EventHeaders.ACTOR, event.getActor());
        return record;
    }

    private static void header(ProducerRecord<String, String> record, String name, String value) {
        if (value != null && !value.isBlank()) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private record InFlight(OutboxEvent event, CompletableFuture<SendResult<String, String>> ack) {
    }

    /** Outcome of one lease. */
    record LeaseResult(int leased, int published, int failed) {
        static final LeaseResult EMPTY = new LeaseResult(0, 0, 0);
    }
}
