package com.opencrm.contract.repository;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.event.QuoteAcceptedEventConsumer;
import com.opencrm.contract.service.SubscriptionRateApplyService;
import com.opencrm.contract.service.SubscriptionService;
import com.opencrm.contract.acl.ErpAdapter;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/** Real PostgreSQL/JPA proofs: migration/query compilation, CAS ownership and rollback. */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test", "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false", "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class SubscriptionRatePersistenceIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=contract");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired SubscriptionRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired SubscriptionRatePeriodRepository rates;
    @Autowired QuoteAcceptedEventConsumer consumer;
    @Autowired SubscriptionRateApplyService apply;
    @Autowired SubscriptionService subscriptions;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean ErpAdapter erp;

    @Test void concurrentNewHeadersAndHeaderlessReplayShareOneCommercialAcceptance() throws Exception {
        var event = acceptedFixture();
        var payload = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(event);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = record(payload, UUID.randomUUID().toString());
            var second = record(payload, UUID.randomUUID().toString());
            var results = executor.invokeAll(List.<Callable<Void>>of(
                    () -> { consumer.handleQuoteAccepted(first); return null; },
                    () -> { consumer.handleQuoteAccepted(second); return null; }));
            for (var result : results) result.get();
        }
        consumer.handleQuoteAccepted(record(payload, null));
        var subscription = repository.findBySubscriptionNumberAndDeletedFalse("SUB-" + event.getQuoteId()).orElseThrow();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(subscription.getId())).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM applied_quote_acceptances WHERE accepted_quote_id=?", Long.class, event.getQuoteId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE quote_id=?", Long.class, event.getQuoteId())).isEqualTo(1L);
    }

    @Test void rejectedCommercialFactsRollBackInboxAndPermitCorrectedSameHeader() throws Exception {
        var event = acceptedFixture();
        String eventId = UUID.randomUUID().toString();
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        event.getLineItems().getFirst().getRamp().getFirst().setPeriodAmount(new BigDecimal("1"));
        assertThatThrownBy(() -> consumer.handleQuoteAccepted(record(mapper.writeValueAsString(event), eventId))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id=?", Long.class, eventId)).isZero();
        assertThat(repository.existsBySubscriptionNumber("SUB-" + event.getQuoteId())).isFalse();
        event.getLineItems().getFirst().getRamp().getFirst().setPeriodAmount(new BigDecimal("24000"));
        consumer.handleQuoteAccepted(record(mapper.writeValueAsString(event), eventId));
        assertThat(repository.existsBySubscriptionNumber("SUB-" + event.getQuoteId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_events WHERE event_id=?", Long.class, eventId)).isEqualTo(1L);
    }

    @Test void actualGoldenFactoryEventPersistsCreditAndRunRatesAsSeparateFacts() throws Exception {
        var event = acceptedFixture("r4-accepted-event");
        var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer.handleQuoteAccepted(record(mapper.writeValueAsString(event), UUID.randomUUID().toString()));
        var subscription = repository.findBySubscriptionNumberAndDeletedFalse("SUB-" + event.getQuoteId()).orElseThrow();
        var persisted = rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(subscription.getId());
        assertThat(persisted).hasSize(4);
        assertThat(subscription.getArr()).isEqualByComparingTo("18000");
        var credited = persisted.stream().filter(period -> period.getMigrationCredit() != null).findFirst().orElseThrow();
        assertThat(credited.getGrossPeriodAmount()).isEqualByComparingTo("12000");
        assertThat(credited.getMigrationCredit()).isEqualByComparingTo("3000");
        assertThat(credited.getPeriodAmount()).isEqualByComparingTo("9000");
        assertThat(credited.getNetMonthly()).isEqualByComparingTo("1000");
        assertThat(credited.getNetUnitPrice()).isEqualByComparingTo("100");
        assertThat(credited.getProductType()).isEqualTo("ONPREM_SUPPORT");
        assertThat(outboxCount(subscription)).isZero();
    }

    @Test void twoApplyingReplicasCommitOneMarkerAndOneOutboxEvent() throws Exception {
        var subscription = create(false);
        var period = duePeriod(subscription);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var attempts = executor.invokeAll(List.<Callable<Integer>>of(
                    () -> apply.applyDue(subscription.getId()), () -> apply.applyDue(subscription.getId())));
            assertThat(attempts.get(0).get() + attempts.get(1).get()).isEqualTo(1);
        }
        assertThat(rates.findById(period.getId()).orElseThrow().isApplied()).isTrue();
        assertThat(outboxCount(subscription)).isEqualTo(1L);
        assertThat(repository.findById(subscription.getId()).orElseThrow().getMrr()).isEqualByComparingTo("250");
    }

    @Test void rollbackKeepsRateMarkerFinancialStateAndOutboxAtomic() {
        var subscription = create(false);
        var period = duePeriod(subscription);
        tx().executeWithoutResult(status -> {
            assertThat(apply.applyDue(subscription.getId())).isEqualTo(1);
            assertThat(outboxCount(subscription)).isEqualTo(1L);
            status.setRollbackOnly();
        });
        assertThat(rates.findById(period.getId()).orElseThrow().isApplied()).isFalse();
        assertThat(outboxCount(subscription)).isZero();
        assertThat(repository.findById(subscription.getId()).orElseThrow().getMrr()).isEqualByComparingTo("100");
        assertThat(apply.applyDue(subscription.getId())).isEqualTo(1);
        assertThat(outboxCount(subscription)).isEqualTo(1L);
    }

    @Test void manualActivationMustAcquireCommercialLockBeforeApplyingRatesOrCallingErp() throws Exception {
        var subscription = create(false);
        tx().executeWithoutResult(status -> {
            var fresh = repository.findByIdForUpdate(subscription.getId()).orElseThrow();
            fresh.setStatus(SubscriptionStatus.PENDING);
            repository.saveAndFlush(fresh);
        });
        duePeriod(subscription);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var erpCalled = new CountDownLatch(1);
        var activatingStarted = new CountDownLatch(1);
        doAnswer(call -> { erpCalled.countDown(); return "ERP-test"; }).when(erp).syncToErp(any());
        boolean externalBeforeRelease;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var holder = executor.submit(() -> tx().executeWithoutResult(status -> {
                repository.findByIdForUpdate(subscription.getId()).orElseThrow();
                locked.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("lock test timed out"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var activating = executor.submit(() -> { activatingStarted.countDown(); return subscriptions.activate(subscription.getId()); });
            assertThat(activatingStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try { externalBeforeRelease = erpCalled.await(400, TimeUnit.MILLISECONDS); }
            finally { release.countDown(); }
            holder.get(5, TimeUnit.SECONDS);
            activating.get(5, TimeUnit.SECONDS);
        }
        assertThat(externalBeforeRelease).as("activation must wait for the same subscription lock as mutation/scheduler").isFalse();
        assertThat(erpCalled.getCount()).isZero();
        assertThat(outboxCount(subscription)).isEqualTo(1L);
    }

    private QuoteAcceptedEvent acceptedFixture() throws Exception {
        return acceptedFixture("ramp-new");
    }
    private QuoteAcceptedEvent acceptedFixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/accepted-v1/" + name + ".json")) {
            var event = new ObjectMapper().registerModule(new JavaTimeModule()).readValue(input, QuoteAcceptedEvent.class);
            event.setQuoteId(UUID.randomUUID().toString());
            if (event.getQuote() != null) event.getQuote().setQuoteId(event.getQuoteId());
            return event;
        }
    }
    private ConsumerRecord<String, String> record(String payload, String eventId) {
        var record = new ConsumerRecord<String, String>(KafkaTopics.QUOTE_STATUS_ACCEPTED, 0, Math.abs(UUID.randomUUID().getLeastSignificantBits()), "fixture", payload);
        if (eventId != null) record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        return record;
    }
    private SubscriptionRatePeriod duePeriod(Subscription subscription) {
        LocalDate start = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1);
        return tx().execute(status -> {
            var locked = repository.findByIdForUpdate(subscription.getId()).orElseThrow();
            locked.setStartDate(start);
            locked.setEndDate(start.plusMonths(12).minusDays(1));
            repository.saveAndFlush(locked);
            return rates.saveAndFlush(SubscriptionRatePeriod.builder().subscriptionId(subscription.getId())
                .acceptedLineUuid(UUID.randomUUID().toString()).sequence(1).startDate(start).billingAnchorDate(start)
                .startMonth(1).months(12).quantity(new BigDecimal("10")).netUnitPrice(new BigDecimal("25"))
                .endDate(locked.getEndDate()).effectiveEndDate(locked.getEndDate()).netMonthly(new BigDecimal("250"))
                .annualRunRate(new BigDecimal("3000")).periodAmount(new BigDecimal("3000"))
                .currencyCode("USD").productType("SAAS_SEAT_NAMED").lineItemType("SAAS_SEAT_NAMED")
                .billingFrequency("MONTHLY").provenance("ACCEPTED_PRICED").build());
        });
    }
    private long outboxCount(Subscription subscription) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND topic=?", Long.class,
                subscription.getSubscriptionNumber(), KafkaTopics.SUBSCRIPTION_RAMP_APPLIED);
    }

    @Test void twoCompetingClaimsHaveOneOwnerAndAdvanceOptimisticVersion() throws Exception {
        Subscription inserted = create(false);
        long oldVersion = inserted.getVersion();
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Integer>> attempts = List.of(
                    () -> tx().execute(status -> repository.claimErpSync(inserted.getId(), "owner-a", Instant.now())),
                    () -> tx().execute(status -> repository.claimErpSync(inserted.getId(), "owner-b", Instant.now())));
            var results = executor.invokeAll(attempts);
            assertThat(results.get(0).get() + results.get(1).get()).isEqualTo(1);
        }
        var claimed = repository.findById(inserted.getId()).orElseThrow();
        assertThat(claimed.getErpSyncStatus()).isEqualTo("IN_PROGRESS");
        assertThat(claimed.getErpSyncClaimRevision()).isEqualTo(0L);
        assertThat(claimed.getVersion()).isEqualTo(oldVersion + 1);
    }

    @Test void olderCompletionCannotOverwriteCommercialMutationQueuedAfterClaim() {
        var inserted = create(false);
        assertThat(tx().<Integer>execute(status -> repository.claimErpSync(inserted.getId(), "old-owner", Instant.now()))).isEqualTo(1);
        long claimedVersion = repository.findById(inserted.getId()).orElseThrow().getVersion();
        tx().executeWithoutResult(status -> {
            var fresh = repository.findByIdForUpdate(inserted.getId()).orElseThrow();
            fresh.setCommercialRevision(1L);
            fresh.setMrr(new BigDecimal("500"));
            fresh.setArr(new BigDecimal("6000"));
            fresh.setErpSyncStatus("QUEUED");
            fresh.setErpSyncClaimToken(null);
            fresh.setErpSyncClaimRevision(null);
            repository.saveAndFlush(fresh);
        });
        assertThat(tx().<Integer>execute(status -> repository.finishErpSyncClaim(inserted.getId(), "old-owner", 0L, "SYNCED", Instant.now())))
                .isZero();
        var fresh = repository.findById(inserted.getId()).orElseThrow();
        assertThat(fresh.getErpSyncStatus()).isEqualTo("QUEUED");
        assertThat(fresh.getMrr()).isEqualByComparingTo("500");
        assertThat(fresh.getCommercialRevision()).isEqualTo(1L);
        assertThat(fresh.getVersion()).isGreaterThan(claimedVersion);
    }

    @Test void failedTransactionRollsBackClaimAndVersionTogether() {
        var inserted = create(false);
        tx().executeWithoutResult(status -> {
            assertThat(repository.claimErpSync(inserted.getId(), "rolled-back", Instant.now())).isEqualTo(1);
            status.setRollbackOnly();
        });
        var fresh = repository.findById(inserted.getId()).orElseThrow();
        assertThat(fresh.getErpSyncStatus()).isEqualTo("QUEUED");
        assertThat(fresh.getErpSyncClaimToken()).isNull();
        assertThat(fresh.getVersion()).isEqualTo(inserted.getVersion());
    }

    @Test void deletedQueuedRowsCannotStarveBoundedErpSelection() {
        tx().executeWithoutResult(status -> {
            for (var subscription : repository.findAll()) {
                subscription.setErpSyncStatus("SYNCED");
                repository.save(subscription);
            }
        });
        var deleted = create(true);
        var live = create(false);
        assertThat(repository.findIdsByErpSyncStatus("QUEUED", PageRequest.of(0, 1))).containsExactly(live.getId());
        assertThat(tx().<Integer>execute(status -> repository.claimErpSync(deleted.getId(), "never", Instant.now()))).isZero();
    }

    @Test void migrationBackfillsRealLegacyFactsWithoutInventingUnitPriceOrQuantity() throws Exception {
        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=r3_backfill_it";
        var baseline = org.flywaydb.core.Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("r3_backfill_it").schemas("r3_backfill_it").locations("classpath:db/migration")
                .target(org.flywaydb.core.api.MigrationVersion.fromVersion("1.5")).load();
        baseline.migrate();
        try (var connection = java.sql.DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO subscriptions(uuid,subscription_number,customer_id,quote_id,plan_name,"
                    + "start_date,end_date,mrr,arr,currency_code,status) VALUES "
                    + "('11111111-1111-4111-8111-000000000399','SUB-legacy','C-DB','Q-legacy','Legacy plan',"
                    + "'2027-01-31','2029-01-30',NULL,1200,'EUR','ACTIVE')");
        }
        org.flywaydb.core.Flyway.configure().dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema("r3_backfill_it").schemas("r3_backfill_it").locations("classpath:db/migration").load().migrate();
        try (var connection = java.sql.DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT * FROM subscription_rate_periods")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getBigDecimal("net_monthly")).isEqualByComparingTo("100");
            assertThat(result.getBigDecimal("annual_run_rate")).isEqualByComparingTo("1200");
            assertThat(result.getObject("quantity")).isNull();
            assertThat(result.getObject("net_unit_price")).isNull();
            assertThat(result.getObject("period_amount")).isNull();
            assertThat(result.getString("currency_code")).isEqualTo("EUR");
            assertThat(result.getDate("start_date").toLocalDate()).isEqualTo(LocalDate.of(2027,1,31));
            assertThat(result.getDate("end_date").toLocalDate()).isEqualTo(LocalDate.of(2029,1,30));
            assertThat(result.getBoolean("applied")).isTrue();
            assertThat(result.getString("provenance")).isEqualTo("LEGACY_AGGREGATE");
            assertThat(result.next()).isFalse();
        }
    }

    private Subscription create(boolean deleted) {
        String identity = UUID.randomUUID().toString();
        return tx().execute(status -> repository.saveAndFlush(Subscription.builder().subscriptionNumber("SUB-" + identity)
                .customerId("C-DB").quoteId(identity).planName("Database fixture")
                .startDate(LocalDate.of(2027, 1, 1)).endDate(LocalDate.of(2027, 12, 31))
                .status(SubscriptionStatus.ACTIVE).mrr(new BigDecimal("100")).arr(new BigDecimal("1200"))
                .erpSyncStatus("QUEUED").deleted(deleted).build()));
    }
    private TransactionTemplate tx() { return new TransactionTemplate(transactions); }
}
