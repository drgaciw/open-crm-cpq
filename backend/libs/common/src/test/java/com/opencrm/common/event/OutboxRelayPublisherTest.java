package com.opencrm.common.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-018 D2/D3 relay behaviour. Leasing SQL and concurrency run against PostgreSQL in quote-service's
 * {@code OutboxRelayLeasingIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRelayPublisher (ADR-018 D2/D3)")
class OutboxRelayPublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-22T12:00:00Z");

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final RecordingObserver observer = new RecordingObserver();
    private OutboxProperties.Relay properties;
    private OutboxRelayPublisher relay;

    @BeforeEach
    void setUp() {
        properties = new OutboxProperties().getRelay();
        properties.setAckTimeout(Duration.ofMillis(200));
        relay = new OutboxRelayPublisher(repository, kafkaTemplate,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), properties, observer,
                Clock.fixed(NOW, ZoneOffset.UTC), "quote-service");
    }

    private static OutboxEvent row(long id, String aggregateId) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("Quote")
                .aggregateId(aggregateId)
                .eventType("cpq.quote.status.accepted")
                .payload("{\"id\":" + id + "}")
                .build();
        event.setId(id);
        return event;
    }

    private static CompletableFuture<SendResult<String, String>> acked() {
        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<SendResult<String, String>> rejected(String message) {
        return CompletableFuture.failedFuture(new IllegalStateException(message));
    }

    @SuppressWarnings("unchecked")
    private static ProducerRecord<String, String> anyRecord() {
        return any(ProducerRecord.class);
    }

    private static ProducerRecord<String, String> keyed(String key) {
        return argThat(record -> record != null && key.equals(record.key()));
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("D4 headers: the record carries eventId (the row UUID), occurredAt and producer; topic, key and payload are unchanged")
    void recordCarriesEnvelopeHeaders() {
        OutboxEvent event = row(1, "q-1");
        event.setUuid("5f0c9d7e-0000-4000-8000-000000000001");
        event.setCreatedAt(Instant.parse("2026-09-22T11:59:00Z"));

        ProducerRecord<String, String> record = relay.toRecord(event);

        assertThat(record.topic()).isEqualTo("cpq.quote.status.accepted");
        assertThat(record.key()).isEqualTo("q-1");
        assertThat(record.value()).isEqualTo("{\"id\":1}");
        assertThat(header(record, EventHeaders.EVENT_ID)).isEqualTo("5f0c9d7e-0000-4000-8000-000000000001");
        assertThat(header(record, EventHeaders.OCCURRED_AT)).isEqualTo("2026-09-22T11:59:00Z");
        assertThat(header(record, EventHeaders.PRODUCER)).isEqualTo("quote-service");
    }

    @Test
    @DisplayName("an empty lease sends nothing")
    void emptyLeaseSendsNothing() {
        when(repository.leaseDueBatch(any(), anyInt())).thenReturn(List.of());

        relay.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyRecord());
        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("a row is marked published only after the broker acknowledged it")
    void acknowledgedRowIsPublished() {
        OutboxEvent event = row(1, "q-1");
        when(repository.leaseDueBatch(NOW, 100)).thenReturn(List.of(event)).thenReturn(List.of());
        when(kafkaTemplate.send(keyed("q-1"))).thenReturn(acked());

        relay.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublished()).isTrue();
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        assertThat(observer.published).containsExactly(1L);
        verify(repository).saveAll(List.of(event));
    }

    @Test
    @DisplayName("a failed send future leaves the row unpublished, records the error and backs off")
    void failedSendFutureLeavesRowUnpublished() {
        OutboxEvent event = row(1, "q-1");
        when(repository.leaseDueBatch(NOW, 100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyRecord())).thenReturn(rejected("NOT_ENOUGH_REPLICAS"));

        relay.publishPendingEvents();

        assertThat(event.getPublished()).isFalse();
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getErrorMessage()).contains("NOT_ENOUGH_REPLICAS");
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(observer.failed).containsExactly(1L);
        verify(repository, times(1)).leaseDueBatch(any(), anyInt());
    }

    @Test
    @DisplayName("no acknowledgement within ack-timeout counts as a failed attempt")
    void missingAckTimesOut() {
        OutboxEvent event = row(1, "q-1");
        when(repository.leaseDueBatch(NOW, 100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyRecord())).thenReturn(new CompletableFuture<>());

        relay.publishPendingEvents();

        assertThat(event.getPublished()).isFalse();
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getErrorMessage()).contains("No broker acknowledgement within PT0.2S");
    }

    @Test
    @DisplayName("a synchronous send failure stops the lease; later rows are neither sent nor charged an attempt")
    void synchronousFailureStopsTheLease() {
        OutboxEvent first = row(1, "q-1");
        OutboxEvent second = row(2, "q-2");
        when(repository.leaseDueBatch(NOW, 100)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(keyed("q-1")))
                .thenThrow(new IllegalStateException("Topic not present in metadata after 10000 ms"));

        relay.publishPendingEvents();

        verify(kafkaTemplate, never()).send(keyed("q-2"));
        assertThat(first.getRetryCount()).isEqualTo(1);
        assertThat(first.getErrorMessage()).contains("Topic not present in metadata");
        assertThat(second.getRetryCount()).isZero();
        assertThat(second.getNextAttemptAt()).isNull();
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("one failure in a lease does not stop the other rows being published")
    void mixedLeaseRecordsEachOutcome() {
        OutboxEvent ok = row(1, "q-1");
        OutboxEvent bad = row(2, "q-2");
        when(repository.leaseDueBatch(NOW, 100)).thenReturn(List.of(ok, bad)).thenReturn(List.of());
        when(kafkaTemplate.send(keyed("q-1"))).thenReturn(acked());
        when(kafkaTemplate.send(keyed("q-2"))).thenReturn(rejected("RecordTooLarge"));

        relay.publishPendingEvents();

        assertThat(ok.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(bad.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(bad.getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the attempt that reaches max-attempts marks the row FAILED")
    void retryLimitMarksRowFailed() {
        OutboxEvent event = row(1, "q-1");
        event.setRetryCount(properties.getMaxAttempts() - 1);
        when(repository.leaseDueBatch(NOW, 100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyRecord())).thenReturn(rejected("still down"));

        relay.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getPublished()).isFalse();
        assertThat(event.getRetryCount()).isEqualTo(properties.getMaxAttempts());
        assertThat(event.getNextAttemptAt()).isNull();
        assertThat(observer.exhausted).containsExactly(1L);
    }

    @Test
    @DisplayName("a tick keeps leasing while leases make progress, which drains one aggregate's burst")
    void drainsWhileLeasesMakeProgress() {
        OutboxEvent first = row(1, "q-1");
        OutboxEvent second = row(2, "q-1");
        when(repository.leaseDueBatch(NOW, 100))
                .thenReturn(List.of(first))
                .thenReturn(List.of(second))
                .thenReturn(List.of());
        when(kafkaTemplate.send(anyRecord())).thenReturn(acked());

        relay.publishPendingEvents();

        assertThat(first.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(second.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(repository, times(3)).leaseDueBatch(NOW, 100);
    }

    @Test
    @DisplayName("draining stops at max-drain-iterations")
    void drainIsBounded() {
        properties.setMaxDrainIterations(2);
        when(repository.leaseDueBatch(NOW, 100)).thenAnswer(inv -> List.of(row(System.nanoTime(), "q-" + System.nanoTime())));
        when(kafkaTemplate.send(anyRecord())).thenReturn(acked());

        relay.publishPendingEvents();

        verify(repository, times(2)).leaseDueBatch(NOW, 100);
    }

    @Test
    @DisplayName("backoff doubles per attempt and is capped")
    void backoffDoublesAndCaps() {
        Duration initial = Duration.ofSeconds(5);
        Duration max = Duration.ofMinutes(5);

        assertThat(OutboxRelayPublisher.backoff(1, initial, max)).isEqualTo(Duration.ofSeconds(5));
        assertThat(OutboxRelayPublisher.backoff(2, initial, max)).isEqualTo(Duration.ofSeconds(10));
        assertThat(OutboxRelayPublisher.backoff(4, initial, max)).isEqualTo(Duration.ofSeconds(40));
        assertThat(OutboxRelayPublisher.backoff(7, initial, max)).isEqualTo(max);
        assertThat(OutboxRelayPublisher.backoff(1_000, initial, max)).isEqualTo(max);
    }

    @Test
    @DisplayName("cleanup is one delete of published rows older than the retention period")
    void cleanupDeletesPublishedRowsPastRetention() {
        when(repository.deletePublishedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(3);

        relay.cleanupOldEvents();

        verify(repository).deletePublishedBefore(NOW.minus(Duration.ofDays(7)));
    }

    private static final class RecordingObserver implements OutboxRelayObserver {
        final List<Long> published = new ArrayList<>();
        final List<Long> failed = new ArrayList<>();
        final List<Long> exhausted = new ArrayList<>();

        @Override
        public void published(OutboxEvent event) {
            published.add(event.getId());
        }

        @Override
        public void attemptFailed(OutboxEvent event, boolean exhaustedNow) {
            failed.add(event.getId());
            if (exhaustedNow) {
                exhausted.add(event.getId());
            }
        }
    }
}
