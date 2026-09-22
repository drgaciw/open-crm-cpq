package com.opencrm.quote.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxBacklogProbe;
import com.opencrm.common.event.OutboxEvent;
import com.opencrm.common.event.OutboxEventRepository;
import com.opencrm.common.event.OutboxHealthIndicator;
import com.opencrm.common.event.OutboxProperties;
import com.opencrm.common.event.OutboxRelayObserver;
import com.opencrm.common.event.OutboxRelayPublisher;
import com.opencrm.common.event.OutboxStatus;
import com.opencrm.quote.QuoteServiceApplication;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ADR-018 D2/D3 acceptance against real PostgreSQL (Testcontainers, quote-service Flyway migrations):
 * <ul>
 *   <li>two concurrent leases never share a row ({@code FOR UPDATE SKIP LOCKED});</li>
 *   <li>only the oldest unpublished row of an aggregate is leased, and a FAILED row blocks its aggregate;</li>
 *   <li>a failing send future leaves the row unpublished;</li>
 *   <li>an aggregate's rows are published in order when an earlier row fails;</li>
 *   <li>the {@code outbox} indicator is DOWN with an aged pending row.</li>
 * </ul>
 * The relay under test is built directly with a mocked {@link KafkaTemplate}; the service's own relay stays disabled.
 */
@Tag("integration")
@ActiveProfiles("test")
@SpringBootTest(
        classes = QuoteServiceApplication.class,
        properties = {
            // Mirrors application.yml; the Surefire system property would otherwise drop the Event Hubs Kafka exclude.
            "spring.autoconfigure.exclude="
                    + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
@DisplayName("ADR-018 D2/D3: outbox leasing and relay against PostgreSQL")
class OutboxRelayLeasingIntegrationTest {

    private static final String TOPIC = KafkaTopics.QUOTE_STATUS_ACCEPTED;

    @Autowired OutboxEventRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxHealthIndicator serviceHealthIndicator;

    private TransactionTemplate tx;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void cleanOutbox() {
        tx = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM outbox_events");
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    private long insert(String aggregateId, String payload) {
        return tx.execute(status -> repository.save(OutboxEvent.builder()
                .aggregateType("Quote")
                .aggregateId(aggregateId)
                .eventType(TOPIC)
                .payload(payload)
                .build()).getId());
    }

    private List<Long> lease(Instant now, int limit) {
        return tx.execute(status -> ids(repository.leaseDueBatch(now, limit)));
    }

    private static List<Long> ids(List<OutboxEvent> rows) {
        return rows.stream().map(OutboxEvent::getId).toList();
    }

    private OutboxEvent reload(long id) {
        return repository.findById(id).orElseThrow();
    }

    private OutboxRelayPublisher relay(KafkaTemplate<String, String> kafka, Instant now) {
        OutboxProperties.Relay settings = new OutboxProperties().getRelay();
        settings.setAckTimeout(Duration.ofSeconds(2));
        return new OutboxRelayPublisher(repository, kafka, tx, settings, OutboxRelayObserver.NOOP,
                Clock.fixed(now, ZoneOffset.UTC), "quote-service");
    }

    @Test
    @DisplayName("D3: two concurrent leases never share a row, and the second does not wait for the first")
    void concurrentLeasesAreDisjoint() throws Exception {
        List<Long> all = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            all.add(insert("agg-" + i, "{}"));
        }
        Instant now = Instant.now().plusSeconds(1);
        CountDownLatch firstLeased = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<List<Long>> first = executor.submit(() -> tx.execute(status -> {
            List<Long> leased = ids(repository.leaseDueBatch(now, 8));
            firstLeased.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return leased;
        }));
        assertThat(firstLeased.await(20, TimeUnit.SECONDS)).isTrue();

        long started = System.nanoTime();
        List<Long> second = lease(now, 20);
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        release.countDown();
        List<Long> firstIds = first.get(20, TimeUnit.SECONDS);

        assertThat(firstIds).hasSize(8);
        assertThat(second).hasSize(12).doesNotContainAnyElementsOf(firstIds);
        Set<Long> union = new HashSet<>(firstIds);
        union.addAll(second);
        assertThat(union).containsExactlyInAnyOrderElementsOf(all);
        assertThat(waitedMillis).as("SKIP LOCKED must not block on the other lease").isLessThan(5_000);
    }

    @Test
    @DisplayName("D3: only the oldest unpublished row of each aggregate is leased")
    void onlyTheHeadOfEachAggregateIsLeased() {
        long x1 = insert("X", "{\"n\":1}");
        long x2 = insert("X", "{\"n\":2}");
        long x3 = insert("X", "{\"n\":3}");
        long y1 = insert("Y", "{\"n\":1}");
        Instant now = Instant.now().plusSeconds(1);

        assertThat(lease(now, 10)).containsExactly(x1, y1);

        jdbc.update("UPDATE outbox_events SET status = 'PUBLISHED', published = TRUE, published_at = now() WHERE id = ?", x1);
        assertThat(lease(now, 10)).containsExactly(x2, y1).doesNotContain(x3);
    }

    @Test
    @DisplayName("D2/D3: a FAILED row blocks the rest of its aggregate, not other aggregates")
    void failedRowBlocksItsAggregate() {
        long x1 = insert("X", "{}");
        insert("X", "{}");
        long y1 = insert("Y", "{}");
        jdbc.update("UPDATE outbox_events SET status = 'FAILED', retry_count = 10 WHERE id = ?", x1);

        assertThat(lease(Instant.now().plusSeconds(1), 10)).containsExactly(y1);
    }

    @Test
    @DisplayName("D2: a row backing off is not due until next_attempt_at")
    void backingOffRowIsNotDue() {
        long x1 = insert("X", "{}");
        Instant retryAt = Instant.now().plus(1, ChronoUnit.HOURS);
        jdbc.update("UPDATE outbox_events SET next_attempt_at = ? WHERE id = ?", java.sql.Timestamp.from(retryAt), x1);

        assertThat(lease(Instant.now(), 10)).isEmpty();
        assertThat(lease(retryAt.plusSeconds(1), 10)).containsExactly(x1);
    }

    /** Matches the q-order record on TOPIC with {@code value}, i.e. one specific outbox row. */
    private static ProducerRecord<String, String> payload(String value) {
        return argThat(r -> r != null && TOPIC.equals(r.topic()) && "q-order".equals(r.key()) && value.equals(r.value()));
    }

    @Test
    @DisplayName("D2: a failing send future leaves the row unpublished in the database")
    @SuppressWarnings("unchecked")
    void failingSendLeavesRowUnpublished() {
        long id = insert("q-fail", "{}");
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker rejected")));
        Instant now = Instant.now().plusSeconds(1);

        relay(kafka, now).publishPendingEvents();

        OutboxEvent row = reload(id);
        assertThat(row.getPublished()).isFalse();
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(row.getRetryCount()).isEqualTo(1);
        assertThat(row.getErrorMessage()).contains("broker rejected");
        assertThat(row.getNextAttemptAt()).isAfter(now);
    }

    @Test
    @DisplayName("D2: an aggregate's rows are published in order when the first attempt fails")
    @SuppressWarnings("unchecked")
    void aggregateOrderSurvivesAFailure() {
        long first = insert("q-order", "{\"n\":1}");
        long second = insert("q-order", "{\"n\":2}");
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, String>> acked = CompletableFuture.completedFuture(null);
        when(kafka.send(payload("{\"n\":1}")))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("transient")))
                .thenReturn(acked);
        when(kafka.send(payload("{\"n\":2}"))).thenReturn(acked);
        Instant now = Instant.now().plusSeconds(1);

        relay(kafka, now).publishPendingEvents();

        verify(kafka, never()).send(payload("{\"n\":2}"));
        assertThat(reload(second).getStatus()).isEqualTo(OutboxStatus.PENDING);

        // After the backoff, one tick publishes both, first then second.
        relay(kafka, now.plus(1, ChronoUnit.HOURS)).publishPendingEvents();

        InOrder order = inOrder(kafka);
        order.verify(kafka, times(2)).send(payload("{\"n\":1}"));
        order.verify(kafka).send(payload("{\"n\":2}"));
        assertThat(reload(first).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(reload(second).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(reload(first).getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("D1: the outbox indicator is DOWN with an aged pending row, and UNKNOWN while the relay is disabled")
    void healthReflectsAgedBacklog() {
        long id = insert("q-aged", "{}");
        jdbc.update("UPDATE outbox_events SET created_at = now() - interval '1 hour' WHERE id = ?", id);
        OutboxBacklogProbe probe = new OutboxBacklogProbe(repository, Duration.ZERO, Clock.systemUTC());

        OutboxHealthIndicator withRelay = new OutboxHealthIndicator(probe, () -> true, Duration.ofMinutes(10));

        assertThat(withRelay.health().getStatus()).isEqualTo(Status.DOWN);
        assertThat(serviceHealthIndicator.health().getStatus())
                .as("quote-service runs with the relay disabled by default")
                .isEqualTo(Status.UNKNOWN);
    }
}
