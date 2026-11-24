package com.opencrm.contract.asset;

import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.documents.apply.AssetLifecycleReadiness;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DRG-902 AC5 / P02 / AM05 / AM09. Postgres 16: Hibernate validate, dedupe, SKIP LOCKED,
 * check constraints, and an empty-schema migrate to V1_25.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false",
        "opencrm.asset.lifecycle.poll-interval=PT1H",
        "opencrm.asset.lifecycle.initial-delay=PT1H"})
class AssetLifecycleScheduleIntegrationTest {

    private static final String EMPTY_SCHEMA = "lifecycle_empty_v123";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=contract";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired AssetLifecycleSchedule schedule;
    @Autowired AssetLifecycleChangeRepository changes;
    @Autowired AssetLifecycleDueJob job;
    @Autowired AssetLifecycleReadiness readiness;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void migratesAnEmptyDatabaseToV1_25ValidatesAndRemigratesWithNothingPending() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1.25");
        flyway.validate();

        try (Connection admin = admin()) {
            admin.setAutoCommit(true);
            try (var statement = admin.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + EMPTY_SCHEMA + " CASCADE");
                statement.execute("CREATE SCHEMA " + EMPTY_SCHEMA);
            }
        }
        Flyway fresh = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(EMPTY_SCHEMA)
                .defaultSchema(EMPTY_SCHEMA)
                .locations("classpath:db/migration")
                .load();
        MigrateResult first = fresh.migrate();
        assertThat(first.migrationsExecuted).isPositive();
        assertThat(fresh.info().current().getVersion().getVersion()).isEqualTo("1.25");
        fresh.validate();
        assertThat(fresh.info().pending()).isEmpty();
        MigrateResult second = fresh.migrate();
        assertThat(second.migrationsExecuted).isZero();
        assertThat(fresh.info().pending()).isEmpty();

        String rollback = read("/db/rollback/V1_25__drop_asset_lifecycle_changes.sql");
        assertThat(read("/db/migration/V1_25__create_asset_lifecycle_changes.sql"))
                .contains("-- Rollback: db/rollback/V1_25__drop_asset_lifecycle_changes.sql");
        try (Connection admin = admin()) {
            admin.setAutoCommit(true);
            try (var statement = admin.createStatement()) {
                statement.execute("SET search_path TO " + EMPTY_SCHEMA);
                assertThat(tableCount(statement, EMPTY_SCHEMA)).isEqualTo(1);
                for (String chunk : rollback.split(";")) {
                    String sql = chunk.strip();
                    if (!sql.isEmpty()) {
                        statement.execute(sql);
                    }
                }
                assertThat(tableCount(statement, EMPTY_SCHEMA)).isZero();
            }
        }
    }

    @Test
    void dueIndexIsPartialAndLineIndexCoversTheScheduleKey() {
        String due = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'contract' AND indexname = 'idx_asset_lifecycle_due'
                """, String.class);
        String line = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'contract' AND indexname = 'idx_asset_lifecycle_line'
                """, String.class);
        assertThat(due).containsIgnoringCase("where");
        assertThat(due).contains("PENDING");
        assertThat(line).contains("subscription_id").contains("contract_line_id").contains("sequence_no");
    }

    @Test
    void hibernateValidateRoundTripsAPendingChange() {
        long subscriptionId = insertSubscription();
        AssetLifecycleChange saved = schedule.enqueue(enqueue(subscriptionId, "round-trip-" + UUID.randomUUID()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSequenceNo()).isPositive();
        assertThat(saved.getChangeUuid()).isNotBlank();
        AssetLifecycleChange loaded = changes.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getKind()).isEqualTo(AssetLifecycleChangeKind.CREATE);
        assertThat(loaded.getStatus()).isEqualTo(AssetLifecycleChangeStatus.PENDING);
        assertThat(loaded.getAccountId()).isEqualTo("acct");
        assertThat(loaded.getPayload()).contains("qty");
        assertThat(loaded.getEffectiveDate()).isEqualTo(LocalDate.parse("2026-12-01"));
    }

    @Test
    void concurrentEnqueueWithTheSameDedupeKeyInsertsOneRow() throws Exception {
        long subscriptionId = insertSubscription();
        String dedupeKey = "concurrent-" + UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<AssetLifecycleChange> attempt = () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                return schedule.enqueue(enqueue(subscriptionId, dedupeKey));
            };
            Future<AssetLifecycleChange> first = pool.submit(attempt);
            Future<AssetLifecycleChange> second = pool.submit(attempt);
            start.countDown();
            AssetLifecycleChange left = first.get(15, TimeUnit.SECONDS);
            AssetLifecycleChange right = second.get(15, TimeUnit.SECONDS);
            assertThat(left.getId()).isEqualTo(right.getId());
            Long rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM asset_lifecycle_changes WHERE dedupe_key = ?",
                    Long.class, dedupeKey);
            assertThat(rows).isEqualTo(1L);
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void twoRunnersLockDisjointDueRows() throws Exception {
        long subscriptionId = insertSubscription();
        LocalDate today = LocalDate.parse("2020-06-01");
        insertChange(subscriptionId, "lock-a-" + UUID.randomUUID(), "AMEND", "PENDING", "DOCUMENT_APPLY", today, 11L);
        insertChange(subscriptionId, "lock-b-" + UUID.randomUUID(), "TERMINATE", "PENDING", "DOCUMENT_APPLY", today, 12L);
        CountDownLatch locked = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<List<Long>> claim = () -> transactions.execute(status -> {
                List<Long> ids = changes.lockDue(today, 1).stream().map(AssetLifecycleChange::getId).toList();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting to release row locks");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while holding the row lock", ex);
                }
                return ids;
            });
            Future<List<Long>> first = pool.submit(claim);
            Future<List<Long>> second = pool.submit(claim);
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            List<Long> left = first.get(10, TimeUnit.SECONDS);
            List<Long> right = second.get(10, TimeUnit.SECONDS);
            assertThat(left).hasSize(1);
            assertThat(right).hasSize(1);
            assertThat(left).doesNotContainAnyElementsOf(right);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void dueQueryOrdersByEffectiveDateThenSequence() {
        long subscriptionId = insertSubscription();
        LocalDate today = LocalDate.parse("2020-01-15");
        insertChange(subscriptionId, "ord-c-" + UUID.randomUUID(), "AMEND", "PENDING", "DOCUMENT_APPLY", today, 4L);
        insertChange(subscriptionId, "ord-a-" + UUID.randomUUID(), "AMEND", "PENDING", "DOCUMENT_APPLY", today.minusDays(1), 9L);
        insertChange(subscriptionId, "ord-b-" + UUID.randomUUID(), "AMEND", "PENDING", "DOCUMENT_APPLY", today, 1L);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        List<Long> sequences = transactions.execute(status -> changes.lockDue(today, 10).stream()
                .map(AssetLifecycleChange::getSequenceNo)
                .toList());
        assertThat(sequences).containsExactly(9L, 1L, 4L);
    }

    @Test
    void earlierFailedRowBlocksTheSameLineOnly() {
        long subscriptionId = insertSubscription();
        LocalDate today = LocalDate.parse("2026-11-24");
        insertChange(subscriptionId, "block-failed-" + UUID.randomUUID(), "AMEND", "FAILED", "DOCUMENT_APPLY",
                today.minusDays(1), 1L);
        insertChange(subscriptionId, "block-later-" + UUID.randomUUID(), "TERMINATE", "PENDING", "DOCUMENT_APPLY",
                today, 2L);
        assertThat(changes.existsEarlierBlocking(subscriptionId, "line-1", today, 2L)).isTrue();
        insertChange(subscriptionId, "block-other-" + UUID.randomUUID(), "AMEND", "PENDING", "ACTIVATION",
                today, 3L, "line-2");
        assertThat(changes.existsEarlierBlocking(subscriptionId, "line-2", today, 3L)).isFalse();
    }

    @Test
    void checkConstraintsRejectUnknownKindStatusAndSource() {
        long subscriptionId = insertSubscription();
        assertThatThrownBy(() -> insertChange(subscriptionId, "bad-kind-" + UUID.randomUUID(),
                "SUSPEND", "PENDING", "ACTIVATION", LocalDate.parse("2026-11-24"), 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(subscriptionId, "bad-status-" + UUID.randomUUID(),
                "CREATE", "WAITING", "ACTIVATION", LocalDate.parse("2026-11-24"), 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChange(subscriptionId, "bad-source-" + UUID.randomUUID(),
                "CREATE", "PENDING", "MANUAL", LocalDate.parse("2026-11-24"), 1L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dueJobStaysInertWhileReadinessIsFalse() {
        assertThat(readiness.isReady()).isFalse();
        long subscriptionId = insertSubscription();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String dedupeKey = "inert-" + UUID.randomUUID();
        insertChange(subscriptionId, dedupeKey, "CREATE", "PENDING", "ACTIVATION", today, 21L);
        long auditsBefore = auditFailures();

        job.tick();

        String status = jdbc.queryForObject(
                "SELECT status FROM asset_lifecycle_changes WHERE dedupe_key = ?", String.class, dedupeKey);
        Integer attempts = jdbc.queryForObject(
                "SELECT attempts FROM asset_lifecycle_changes WHERE dedupe_key = ?", Integer.class, dedupeKey);
        assertThat(status).isEqualTo("PENDING");
        assertThat(attempts).isZero();
        assertThat(auditFailures()).isEqualTo(auditsBefore);
    }

    private long insertSubscription() {
        String uuid = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO subscriptions (
                    uuid, subscription_number, customer_id, quote_id, status, plan_name,
                    start_date, end_date, created_by, version)
                VALUES (?, ?, 'cust', ?, 'ACTIVE', 'plan', DATE '2026-01-01', DATE '2026-12-31', 'it', 0)
                """, uuid, "SUB-" + uuid, "Q-" + uuid);
        Long id = jdbc.queryForObject("SELECT id FROM subscriptions WHERE uuid = ?", Long.class, uuid);
        return id;
    }

    private AssetLifecycleSchedule.Enqueue enqueue(long subscriptionId, String dedupeKey) {
        return new AssetLifecycleSchedule.Enqueue(
                dedupeKey,
                AssetLifecycleChangeKind.CREATE,
                null,
                subscriptionId,
                "line-1",
                "acct",
                LocalDate.parse("2026-12-01"),
                AssetLifecycleChange.Source.ACTIVATION,
                null,
                null,
                "corr",
                "{\"qty\":1}");
    }

    private void insertChange(long subscriptionId, String dedupeKey, String kind, String status, String source,
                              LocalDate effectiveDate, long sequenceNo) {
        insertChange(subscriptionId, dedupeKey, kind, status, source, effectiveDate, sequenceNo, "line-1");
    }

    private void insertChange(long subscriptionId, String dedupeKey, String kind, String status, String source,
                              LocalDate effectiveDate, long sequenceNo, String contractLineId) {
        jdbc.update("""
                INSERT INTO asset_lifecycle_changes (
                    change_uuid, dedupe_key, kind, subscription_id, contract_line_id, account_id,
                    effective_date, sequence_no, source_type, payload, status, attempts, version, is_deleted)
                VALUES (?, ?, ?, ?, ?, 'acct', ?, ?, ?, '{}'::jsonb, ?, 0, 0, false)
                """,
                UUID.randomUUID().toString(), dedupeKey, kind, subscriptionId, contractLineId,
                effectiveDate, sequenceNo, source, status);
    }

    private long auditFailures() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE payload LIKE ?",
                Long.class, "%ASSET_LIFECYCLE_FAILED%");
        return count == null ? 0 : count;
    }

    private static Connection admin() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static int tableCount(java.sql.Statement statement, String schema) throws Exception {
        List<String> names = new ArrayList<>();
        try (var result = statement.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = '" + schema
                        + "' AND table_name = 'asset_lifecycle_changes'")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names.size();
    }

    private static String read(String resource) throws Exception {
        try (InputStream in = AssetLifecycleScheduleIntegrationTest.class.getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
