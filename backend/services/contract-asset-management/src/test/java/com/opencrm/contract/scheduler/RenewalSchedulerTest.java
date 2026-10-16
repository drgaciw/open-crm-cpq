package com.opencrm.contract.scheduler;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenewalSchedulerTest {

    private static final LocalDate TERM_END = LocalDate.of(2027, 5, 31);
    private static final LocalDate WINDOW = LocalDate.of(2027, 5, 1);
    private static final String KEY = "15:2027-05-31:2027-05-01";

    private SubscriptionRepository subscriptions;
    private OutboxEventPublisher publisher;
    private JdbcTemplate jdbc;
    private PlatformTransactionManager transactions;
    private RenewalScheduler scheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        subscriptions = mock(SubscriptionRepository.class);
        publisher = mock(OutboxEventPublisher.class);
        jdbc = mock(JdbcTemplate.class);
        transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(subscriptions.findRenewalEligible(any())).thenReturn(List.of(subscription(15L, TERM_END, WINDOW)));
        scheduler = new RenewalScheduler(subscriptions, publisher, jdbc, transactions);
    }

    @Test
    @DisplayName("two nightly runs write one outbox row keyed by subscription, term end and window")
    void secondNightSkipsTheSameTerm() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1, 0);

        scheduler.checkRenewalEligibility();
        scheduler.checkRenewalEligibility();

        verify(publisher, times(1)).publish(
                eq(RenewalScheduler.AGGREGATE_TYPE),
                eq(KEY),
                eq(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1),
                eq(event(15L, TERM_END, WINDOW)));
    }

    @Test
    @DisplayName("a durable claim already stored for the key is not published again")
    void existingDurableClaimIsNotRepublished() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(0);

        scheduler.checkRenewalEligibility();

        verify(publisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a new term end is a new key and is published")
    void newTermPublishesAgain() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        LocalDate nextTerm = LocalDate.of(2028, 5, 31);
        when(subscriptions.findRenewalEligible(any()))
                .thenReturn(List.of(subscription(15L, TERM_END, WINDOW)))
                .thenReturn(List.of(subscription(15L, nextTerm, WINDOW)));

        scheduler.checkRenewalEligibility();
        scheduler.checkRenewalEligibility();

        verify(publisher).publish(
                eq(RenewalScheduler.AGGREGATE_TYPE),
                eq("15:2028-05-31:2027-05-01"),
                eq(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1),
                eq(event(15L, nextTerm, WINDOW)));
    }

    @Test
    @DisplayName("a moved renewal window is a new key and is published")
    void movedWindowPublishesAgain() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        LocalDate moved = LocalDate.of(2027, 4, 1);
        when(subscriptions.findRenewalEligible(any())).thenReturn(List.of(subscription(15L, TERM_END, moved)));

        scheduler.checkRenewalEligibility();

        verify(publisher).publish(
                eq(RenewalScheduler.AGGREGATE_TYPE),
                eq("15:2027-05-31:2027-04-01"),
                eq(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1),
                eq(event(15L, TERM_END, moved)));
    }

    @Test
    @DisplayName("one subscription's publish failure does not skip the next")
    void publishFailureDoesNotStopTheBatch() {
        when(jdbc.update(anyString(), any(), any(), any(), any())).thenReturn(1);
        when(subscriptions.findRenewalEligible(any())).thenReturn(List.of(
                subscription(15L, TERM_END, WINDOW),
                subscription(16L, TERM_END, WINDOW)));
        doThrow(new IllegalStateException("outbox down"))
                .when(publisher).publish(any(), eq(KEY), any(), any());

        scheduler.checkRenewalEligibility();

        verify(transactions).rollback(any());
        verify(transactions).commit(any());
        verify(publisher).publish(
                eq(RenewalScheduler.AGGREGATE_TYPE),
                eq("16:2027-05-31:2027-05-01"),
                eq(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1),
                eq(event(16L, TERM_END, WINDOW)));
    }

    @Test
    void dedupeKeyUsesSubscriptionIdTermEndAndWindow() {
        assertThat(SubscriptionRenewalEligibleEvent.dedupeKey("15", TERM_END, WINDOW)).isEqualTo(KEY);
        assertThat(SubscriptionRenewalEligibleEvent.dedupeKey("15", TERM_END, null)).isEqualTo("15:2027-05-31:none");
    }

    @Test
    void dedupeKeyRejectsBlankIdNullTermEndColonAndOverlongId() {
        assertThatThrownBy(() -> SubscriptionRenewalEligibleEvent.dedupeKey(" ", TERM_END, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubscriptionRenewalEligibleEvent.dedupeKey("15", null, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubscriptionRenewalEligibleEvent.dedupeKey("15:x", TERM_END, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SubscriptionRenewalEligibleEvent.dedupeKey("s".repeat(90), TERM_END, WINDOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Subscription subscription(long id, LocalDate termEnd, LocalDate window) {
        return Subscription.builder()
                .id(id)
                .subscriptionNumber("SUB-" + id)
                .customerId("C-1")
                .quoteId("Q-1")
                .status(SubscriptionStatus.ACTIVE)
                .planName("Enterprise")
                .usageBand("high")
                .startDate(termEnd.minusYears(1))
                .endDate(termEnd)
                .renewalDate(window)
                .build();
    }

    private static SubscriptionRenewalEligibleEvent event(long id, LocalDate termEnd, LocalDate window) {
        String key = SubscriptionRenewalEligibleEvent.dedupeKey(Long.toString(id), termEnd, window);
        return new SubscriptionRenewalEligibleEvent(Long.toString(id), window, "Enterprise", "high", key);
    }

    /**
     * Two scheduler runs against PostgreSQL. The relay stays off (ADR-018 default); the assertion is the outbox row.
     */
    @Nested
    @Tag("integration")
    @Testcontainers(disabledWithoutDocker = true)
    @SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
            "spring.profiles.active=test",
            "spring.kafka.listener.auto-startup=false",
            "opencrm.erp-sync.enabled=false",
            "opencrm.subscription-ramp.enabled=false",
            "opencrm.outbox.relay.enabled=false"
    })
    class NightlyReplay {

        @Container
        static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

        @DynamicPropertySource
        static void database(DynamicPropertyRegistry registry) {
            registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                    + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=contract");
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }

        @Autowired RenewalScheduler scheduler;
        @Autowired SubscriptionRepository repository;
        @Autowired JdbcTemplate jdbc;
        @Autowired ObjectMapper objectMapper;

        @Test
        @DisplayName("two nightly runs emit one outbox row; a new term emits a second")
        void twoNightsEmitOnceUntilTheTermChanges() throws Exception {
            LocalDate termEnd = LocalDate.now().plusMonths(6);
            LocalDate window = LocalDate.now().minusDays(1);
            Subscription saved = repository.saveAndFlush(eligible("SUB-" + UUID.randomUUID(), termEnd, window));
            String key = SubscriptionRenewalEligibleEvent.dedupeKey(
                    saved.getId().toString(), termEnd, window);

            scheduler.checkRenewalEligibility();
            scheduler.checkRenewalEligibility();

            assertThat(outboxCount(key)).isEqualTo(1L);
            JsonNode payload = objectMapper.readTree(payload(key));
            assertThat(payload.get("dedupeKey").asText()).isEqualTo(key);
            assertThat(payload.get("subscriptionId").asText()).isEqualTo(saved.getId().toString());
            assertThat(jdbc.queryForObject(
                    "SELECT event_type FROM outbox_events WHERE aggregate_id = ?",
                    String.class, key))
                    .isEqualTo(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.topic());

            jdbc.update("DELETE FROM outbox_events WHERE aggregate_id = ?", key);
            scheduler.checkRenewalEligibility();
            assertThat(outboxCount(key)).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM renewal_eligibility_publications WHERE dedupe_key = ?", Long.class, key))
                    .isEqualTo(1L);

            LocalDate nextTerm = termEnd.plusYears(1);
            saved.setEndDate(nextTerm);
            repository.saveAndFlush(saved);
            String nextKey = SubscriptionRenewalEligibleEvent.dedupeKey(
                    saved.getId().toString(), nextTerm, window);

            scheduler.checkRenewalEligibility();

            assertThat(outboxCount(key)).isZero();
            assertThat(outboxCount(nextKey)).isEqualTo(1L);
        }

        @Test
        void upgradeSeedsReceiptsForPreviouslyEmittedCompositeKeys() {
            String schema = "renewal_receipt_upgrade";
            var migration = org.flywaydb.core.Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
            migration.target("1.7").load().migrate();
            String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                    + "currentSchema=" + schema;
            JdbcTemplate upgrade = new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    url, POSTGRES.getUsername(), POSTGRES.getPassword()));
            Long id = upgrade.queryForObject("""
                    INSERT INTO subscriptions(uuid, subscription_number, customer_id, quote_id, plan_name, start_date, end_date)
                    VALUES(gen_random_uuid(), 'UPGRADE', 'customer', 'quote', 'plan', DATE '2026-01-01', DATE '2026-12-31')
                    RETURNING id
                    """, Long.class);
            String key = id + ":2026-12-31:none";
            upgrade.update("""
                    INSERT INTO outbox_events(uuid, aggregate_type, aggregate_id, event_type, payload)
                    VALUES(gen_random_uuid(), 'Subscription', ?, 'cpq.subscription.renewal.eligible', '{}')
                    """, key);
            migration.target(org.flywaydb.core.api.MigrationVersion.LATEST).load().migrate();
            assertThat(upgrade.queryForObject(
                    "SELECT count(*) FROM renewal_eligibility_publications WHERE dedupe_key = ?", Long.class, key))
                    .isEqualTo(1L);
            upgrade.update("DELETE FROM outbox_events WHERE aggregate_id = ?", key);
            assertThat(upgrade.queryForObject(
                    "SELECT count(*) FROM renewal_eligibility_publications WHERE dedupe_key = ?", Long.class, key))
                    .isEqualTo(1L);
        }

        @Test
        void overlappingScansLeaveOneDurableReceiptAndOneOutboxRow() throws Exception {
            LocalDate end = LocalDate.now().plusMonths(7);
            LocalDate window = LocalDate.now().minusDays(1);
            Subscription saved = repository.saveAndFlush(eligible("SUB-RACE-" + UUID.randomUUID(), end, window));
            String key = SubscriptionRenewalEligibleEvent.dedupeKey(saved.getId().toString(), end, window);
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> { start.await(); scheduler.checkRenewalEligibility(); return null; });
                var second = pool.submit(() -> { start.await(); scheduler.checkRenewalEligibility(); return null; });
                start.countDown();
                first.get(30, java.util.concurrent.TimeUnit.SECONDS);
                second.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertThat(outboxCount(key)).isEqualTo(1L);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM renewal_eligibility_publications WHERE dedupe_key = ?", Long.class, key))
                    .isEqualTo(1L);
        }

        private static Subscription eligible(String number, LocalDate termEnd, LocalDate window) {
            return Subscription.builder()
                    .subscriptionNumber(number)
                    .customerId("C-IT")
                    .quoteId("Q-IT")
                    .status(SubscriptionStatus.ACTIVE)
                    .planName("Enterprise")
                    .usageBand("mid")
                    .startDate(termEnd.minusYears(1))
                    .endDate(termEnd)
                    .renewalDate(window)
                    .arr(new BigDecimal("12000.00"))
                    .currencyCode("USD")
                    .build();
        }

        private long outboxCount(String key) {
            Long count = jdbc.queryForObject(
                    "SELECT count(*) FROM outbox_events WHERE aggregate_type = ? AND aggregate_id = ?",
                    Long.class, RenewalScheduler.AGGREGATE_TYPE, key);
            return count == null ? 0L : count;
        }

        private String payload(String key) {
            return jdbc.queryForObject(
                    "SELECT payload FROM outbox_events WHERE aggregate_id = ?",
                    String.class, key);
        }
    }
}
