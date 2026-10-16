package com.opencrm.contract.documents;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PostgreSQL proof for DRG-585: V1_8 does not invent a tenant, the backfill is idempotent,
 * compensating delete removes only marked rows, and V1_7_2 frozen lines stay frozen.
 */
@Tag("integration")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ContractHeaderBackfillIntegrationTest {

    private static final String SCHEMA = "s7_header";
    private static final UUID TENANT = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String DIGEST = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    static JdbcTemplate jdbc;
    static ContractHeaderBackfill backfill;
    static long preexistingSubscriptionId;
    static UUID preexistingContractId;
    static String activeUuid;
    static long activeId;
    static String cancelledUuid;

    @BeforeAll
    static void migrateExistingSubscriptionsBeforeV18() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .target("1.7.2")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(schemaUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        backfill = new ContractHeaderBackfill(jdbc);

        preexistingContractId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO contracts(id, tenant_id, contract_number, customer_account_id, term_model,
                    start_date, end_date, status, last_executed_seq, created_by)
                VALUES (?, ?, 'PRE-1', ?, 'TERMED', ?, ?, 'ACTIVE', 0, 'preexisting')
                """, preexistingContractId, TENANT, UUID.randomUUID(), Date.valueOf(START), Date.valueOf(END));
        preexistingSubscriptionId = insertSubscription("linked", UUID.randomUUID().toString(), "ACTIVE", START, END, false);
        jdbc.update("UPDATE subscriptions SET contract_id = ? WHERE id = ?", preexistingContractId, preexistingSubscriptionId);

        activeUuid = UUID.randomUUID().toString();
        activeId = insertSubscription("active", activeUuid, "ACTIVE", START, END, false);
        jdbc.update("UPDATE subscriptions SET arr = 1200.00, mrr = 100.00 WHERE id = ?", activeId);
        insertSubscription("pending", UUID.randomUUID().toString(), "PENDING", START, END, false);
        insertSubscription("suspended", UUID.randomUUID().toString(), "SUSPENDED", START, END, false);
        insertSubscription("expired", UUID.randomUUID().toString(), "EXPIRED",
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), false);
        cancelledUuid = UUID.randomUUID().toString();
        insertSubscription("cancelled", cancelledUuid, "CANCELLED",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 1), false);
        insertSubscription("deleted", UUID.randomUUID().toString(), "ACTIVE", START, END, true);
        insertSubscription("text-customer", "C-001", "ACTIVE", START, END, false);
        insertSubscription("inverted", UUID.randomUUID().toString(), "ACTIVE", END, START, false);
        jdbc.update("""
                INSERT INTO outbox_events(uuid, aggregate_type, aggregate_id, event_type, payload)
                VALUES (?, 'Subscription', 'sentinel', 'test.sentinel', '{}')
                """, UUID.randomUUID().toString());

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .target("1.8")
                .load()
                .migrate();
    }

    @Test
    @Order(1)
    void migrationDoesNotInventHeadersForExistingSubscriptions() {
        assertThat(latestFlywayVersion()).isEqualTo("1.8");
        assertThat(count("SELECT count(*) FROM flyway_schema_history WHERE version = '1.9'")).isZero();
        assertThat(count("SELECT count(*) FROM contracts WHERE created_by = '" + ContractHeaderBackfill.BACKFILL_ACTOR + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM contracts")).isEqualTo(1L);
        assertThat(backfill.countEligible(TENANT)).isEqualTo(5L);
        assertThat(count("SELECT count(*) FROM contracts")).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM amendment_documents")).isZero();
        assertThat(count("SELECT count(*) FROM outbox_events")).isEqualTo(1L);
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT contract_header_backfill_count(NULL)", Long.class))
                .rootCause()
                .hasMessageContaining(ContractHeaderBackfill.TENANT_REQUIRED);
    }

    @Test
    @Order(2)
    void applyIsIdempotentAndLeavesIneligibleRowsAlone() {
        long documents = count("SELECT count(*) FROM amendment_documents");
        long lines = count("SELECT count(*) FROM amendment_document_lines");
        long events = count("SELECT count(*) FROM amendment_document_events");
        long outbox = count("SELECT count(*) FROM outbox_events");
        long ratePeriods = count("SELECT count(*) FROM subscription_rate_periods");

        assertThat(backfill.apply(TENANT)).isEqualTo(5L);
        assertThat(backfill.countEligible(TENANT)).isZero();
        assertThat(backfill.apply(TENANT)).isZero();
        assertThat(backfill.apply(UUID.randomUUID())).isZero();

        assertThat(count("SELECT count(*) FROM contracts WHERE created_by = '" + ContractHeaderBackfill.BACKFILL_ACTOR + "'"))
                .isEqualTo(5L);
        assertThat(count("SELECT count(DISTINCT contract_number) FROM contracts WHERE tenant_id = ?", TENANT))
                .isEqualTo(6L);
        Map<String, Object> active = headerFor(activeUuid);
        assertThat(active.get("contract_number")).isEqualTo("C-" + activeUuid);
        assertThat(active.get("term_model")).isEqualTo("TERMED");
        assertThat(active.get("status")).isEqualTo("ACTIVE");
        assertThat(date(active.get("start_date"))).isEqualTo(START);
        assertThat(date(active.get("end_date"))).isEqualTo(END);
        assertThat(((Number) active.get("last_executed_seq")).intValue()).isZero();
        assertThat(active.get("created_by")).isEqualTo(ContractHeaderBackfill.BACKFILL_ACTOR);
        assertThat(active.get("tenant_id")).isEqualTo(TENANT);
        assertThat(active.get("customer_account_id")).isEqualTo(UUID.fromString(activeUuid));
        assertThat(active.get("prior_contract_id")).isNull();
        assertThat(active.get("governing_agreement_ref")).isNull();
        assertThat(jdbc.queryForObject("SELECT id FROM subscriptions WHERE uuid = ?", Long.class, activeUuid))
                .isEqualTo(activeId);
        assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE uuid = ?", String.class, activeUuid))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT arr FROM subscriptions WHERE uuid = ?", BigDecimal.class, activeUuid))
                .isEqualByComparingTo("1200.00");

        assertThat(headerFor(cancelledUuid).get("status")).isEqualTo("TERMINATED");
        assertThat(jdbc.queryForObject("SELECT status FROM subscriptions WHERE uuid = ?", String.class, cancelledUuid))
                .isEqualTo("CANCELLED");
        assertThat(statusOfBackfilled("PENDING")).isEqualTo("ACTIVE");
        assertThat(statusOfBackfilled("SUSPENDED")).isEqualTo("ACTIVE");
        assertThat(statusOfBackfilled("EXPIRED")).isEqualTo("EXPIRED");

        assertThat(jdbc.queryForObject(
                "SELECT contract_id FROM subscriptions WHERE id = ?", UUID.class, preexistingSubscriptionId))
                .isEqualTo(preexistingContractId);
        assertThat(count("SELECT count(*) FROM subscriptions WHERE subscription_number LIKE 'SUB-deleted%' AND contract_id IS NULL"))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM subscriptions WHERE customer_id = 'C-001' AND contract_id IS NULL"))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM subscriptions WHERE subscription_number LIKE 'SUB-inverted%' AND contract_id IS NULL"))
                .isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM amendment_documents")).isEqualTo(documents);
        assertThat(count("SELECT count(*) FROM amendment_document_lines")).isEqualTo(lines);
        assertThat(count("SELECT count(*) FROM amendment_document_events")).isEqualTo(events);
        assertThat(count("SELECT count(*) FROM outbox_events")).isEqualTo(outbox);
        assertThat(count("SELECT count(*) FROM subscription_rate_periods")).isEqualTo(ratePeriods);
    }

    @Test
    @Order(3)
    void subscriptionCreatedAfterCutoverGetsAStableHeader() {
        String uuid = UUID.randomUUID().toString();
        long id = insertSubscription("created", uuid, "PENDING", START, END, false);
        assertThat(backfill.countEligible(TENANT)).isEqualTo(1L);
        UUID first = backfill.ensureHeaderAtCreation(id, TENANT);
        UUID second = backfill.ensureHeaderAtCreation(id, TENANT);
        assertThat(second).isEqualTo(first);
        assertThatThrownBy(() -> backfill.ensureHeaderAtCreation(id, UUID.randomUUID()))
                .rootCause()
                .hasMessageContaining(ContractHeaderBackfill.LINKED_HEADER_MISMATCH);
        String differentCustomer = UUID.randomUUID().toString();
        jdbc.update("UPDATE subscriptions SET customer_id = ? WHERE id = ?", differentCustomer, id);
        assertThatThrownBy(() -> backfill.ensureHeaderAtCreation(id, TENANT))
                .rootCause()
                .hasMessageContaining(ContractHeaderBackfill.LINKED_HEADER_MISMATCH);
        jdbc.update("UPDATE subscriptions SET customer_id = ? WHERE id = ?", uuid, id);
        assertThat(jdbc.queryForObject("SELECT contract_id FROM subscriptions WHERE id = ?", UUID.class, id))
                .isEqualTo(first);
        assertThat(backfill.countEligible(TENANT)).isZero();
        assertThat(backfill.apply(TENANT)).isZero();
        Map<String, Object> header = headerFor(uuid);
        assertThat(header.get("contract_id")).isEqualTo(first);
        assertThat(header.get("contract_number")).isEqualTo("C-" + uuid);
        assertThat(header.get("created_by")).isEqualTo(ContractHeaderBackfill.CREATION_ACTOR);
        assertThat(header.get("status")).isEqualTo("ACTIVE");
        assertThat(count("SELECT count(*) FROM contracts WHERE contract_number = ?", "C-" + uuid)).isEqualTo(1L);
        assertThatThrownBy(() -> backfill.ensureHeaderAtCreation(
                insertSubscription("bad-new", "not-a-uuid", "ACTIVE", START, END, false), TENANT))
                .rootCause()
                .hasMessageContaining(ContractHeaderBackfill.NOT_ELIGIBLE);
    }

    @Test
    @Order(4)
    void backfillDoesNotViolateFrozenLineMembership() throws Exception {
        UUID documentId = UUID.randomUUID();
        inTransaction(connection -> {
            try (var document = connection.prepareStatement("""
                    INSERT INTO amendment_documents(id, tenant_id, contract_id, lifecycle_change_id,
                        lifecycle_change_revision, intent, initiation_trigger, provisional_number,
                        document_kind, status, current_revision_no, signature_policy, action_date,
                        contract_effective_date, currency_code, tcv_delta, arr_delta, one_time_total, credit_total,
                        content_digest, evidence_class, legal_hold, is_deleted, created_by)
                    VALUES (?, ?, ?, ?, 1, 'AMEND', 'MANUAL', ?, 'AMENDMENT', 'DRAFT', 1, 'NOT_REQUIRED',
                        ?, ?, 'USD', 0, 0, 0, 0, ?, 'STANDARD', false, false, 'freeze-test')
                    """)) {
                document.setObject(1, documentId);
                document.setObject(2, TENANT);
                document.setObject(3, preexistingContractId);
                document.setObject(4, UUID.randomUUID());
                document.setString(5, "P-" + documentId);
                document.setDate(6, Date.valueOf(START));
                document.setDate(7, Date.valueOf(START));
                document.setString(8, DIGEST);
                document.executeUpdate();
            }
            try (var revision = connection.prepareStatement("""
                    INSERT INTO amendment_document_revisions(document_id, revision_no, tenant_id,
                        lifecycle_change_revision, target_quote_id, content_digest, inputs_snapshot_ref,
                        status_at_close, created_by)
                    VALUES (?, 1, ?, 1, ?, ?, 'snap', 'OPEN', 'freeze-test')
                    """)) {
                revision.setObject(1, documentId);
                revision.setObject(2, TENANT);
                revision.setObject(3, UUID.randomUUID());
                revision.setString(4, DIGEST);
                revision.executeUpdate();
            }
            try (var unit = connection.prepareStatement("""
                    INSERT INTO amendment_application_units
                    (document_id, unit_no, tenant_id, dependency_order, target_type, target_ref,
                     observed_commercial_revision, status, created_by)
                    VALUES (?, 1, ?, 0, 'NONE', 'none', 0, 'PENDING', 'freeze-test')
                    """)) {
                unit.setObject(1, documentId);
                unit.setObject(2, TENANT);
                unit.executeUpdate();
            }
            try (var line = connection.prepareStatement("""
                    INSERT INTO amendment_document_lines
                    (tenant_id, document_id, revision_no, line_no, change_line_ref, action_code, business_category,
                     apply_target_type, quantity_semantics, start_qty, delta_qty, end_qty, effective_from,
                     charge_type, price_as_of, product_external_id, pricebook_version, configuration_hash,
                     policy_slot, application_unit_no, created_by)
                    VALUES (?, ?, 1, 1, ?, 'NO_CHANGE', 'TERMS_AND_CONDITIONS',
                            'NONE', 'ABSOLUTE', 1, 0, 1, ?, 'ONE_TIME', 'EFFECTIVE_DATE',
                            'product', 'book-v1', ?, 'AMENDMENT', 1, 'freeze-test')
                    """)) {
                line.setObject(1, TENANT);
                line.setObject(2, documentId);
                line.setObject(3, UUID.randomUUID());
                line.setDate(4, Date.valueOf(START));
                line.setString(5, DIGEST);
                line.executeUpdate();
            }
        });
        jdbc.update("""
                UPDATE amendment_document_revisions SET frozen_at = now()
                WHERE document_id = ? AND revision_no = 1
                """, documentId);
        long linesBefore = count("SELECT count(*) FROM amendment_document_lines");
        assertThat(backfill.apply(TENANT)).isZero();
        assertThat(count("SELECT count(*) FROM amendment_document_lines")).isEqualTo(linesBefore);
        assertThat(jdbc.queryForObject(
                "SELECT product_external_id FROM amendment_document_lines WHERE document_id = ?",
                String.class, documentId)).isEqualTo("product");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE amendment_document_lines SET product_external_id = 'tamper' WHERE document_id = ?",
                documentId))
                .rootCause()
                .hasMessageContaining("frozen amendment revision");
        assertThat(jdbc.queryForObject(
                "SELECT contract_id FROM subscriptions WHERE id = ?", UUID.class, preexistingSubscriptionId))
                .isEqualTo(preexistingContractId);
    }

    @Test
    @Order(5)
    void compensateRefusesSharedReferencesThenDeletesOnlyBackfilledRows() throws Exception {
        UUID backfilled = jdbc.queryForObject(
                "SELECT contract_id FROM subscriptions WHERE uuid = ?", UUID.class, activeUuid);
        long stray = insertSubscription("stray", UUID.randomUUID().toString(), "ACTIVE", END, START, false);
        jdbc.update("UPDATE subscriptions SET contract_id = ? WHERE id = ?", backfilled, stray);
        assertThatThrownBy(backfill::compensate)
                .rootCause()
                .hasMessageContaining(ContractHeaderBackfill.RESTORE_FROM_BACKUP);
        assertThat(count("SELECT count(*) FROM contracts WHERE created_by = '" + ContractHeaderBackfill.BACKFILL_ACTOR + "'"))
                .isEqualTo(5L);
        jdbc.update("UPDATE subscriptions SET contract_id = NULL WHERE id = ?", stray);

        UUID documentId = UUID.randomUUID();
        inTransaction(connection -> {
            try (var document = connection.prepareStatement("""
                    INSERT INTO amendment_documents(id, tenant_id, contract_id, lifecycle_change_id,
                        lifecycle_change_revision, intent, initiation_trigger, provisional_number,
                        document_kind, status, current_revision_no, signature_policy, action_date,
                        contract_effective_date, currency_code, tcv_delta, arr_delta, one_time_total, credit_total,
                        content_digest, evidence_class, legal_hold, is_deleted, created_by)
                    VALUES (?, ?, ?, ?, 1, 'AMEND', 'MANUAL', ?, 'AMENDMENT', 'DRAFT', 1, 'NOT_REQUIRED',
                        ?, ?, 'USD', 0, 0, 0, 0, ?, 'STANDARD', false, false, 'rollback-test')
                    """)) {
                document.setObject(1, documentId);
                document.setObject(2, TENANT);
                document.setObject(3, backfilled);
                document.setObject(4, UUID.randomUUID());
                document.setString(5, "P-RB-" + documentId);
                document.setDate(6, Date.valueOf(START));
                document.setDate(7, Date.valueOf(START));
                document.setString(8, DIGEST);
                document.executeUpdate();
            }
            try (var revision = connection.prepareStatement("""
                    INSERT INTO amendment_document_revisions(document_id, revision_no, tenant_id,
                        lifecycle_change_revision, target_quote_id, content_digest, inputs_snapshot_ref,
                        status_at_close, created_by)
                    VALUES (?, 1, ?, 1, ?, ?, 'snap', 'OPEN', 'rollback-test')
                    """)) {
                revision.setObject(1, documentId);
                revision.setObject(2, TENANT);
                revision.setObject(3, UUID.randomUUID());
                revision.setString(4, DIGEST);
                revision.executeUpdate();
            }
        });
        assertThatThrownBy(backfill::compensate)
                .rootCause()
                .hasMessageContaining(ContractHeaderBackfill.RESTORE_FROM_BACKUP);
        assertThat(jdbc.queryForObject(
                "SELECT contract_id FROM subscriptions WHERE uuid = ?", UUID.class, activeUuid)).isEqualTo(backfilled);

        inTransaction(connection -> {
            try (var revision = connection.prepareStatement(
                    "DELETE FROM amendment_document_revisions WHERE document_id = ?")) {
                revision.setObject(1, documentId);
                revision.executeUpdate();
            }
            try (var document = connection.prepareStatement(
                    "DELETE FROM amendment_documents WHERE id = ?")) {
                document.setObject(1, documentId);
                document.executeUpdate();
            }
        });

        long creationHeaders = count(
                "SELECT count(*) FROM contracts WHERE created_by = '" + ContractHeaderBackfill.CREATION_ACTOR + "'");
        assertThat(backfill.compensate()).isEqualTo(5L);
        assertThat(backfill.compensate()).isZero();
        assertThat(count("SELECT count(*) FROM contracts WHERE created_by = '" + ContractHeaderBackfill.BACKFILL_ACTOR + "'"))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT contract_id FROM subscriptions WHERE uuid = ?", UUID.class, activeUuid)).isNull();
        assertThat(count(
                "SELECT count(*) FROM contracts WHERE created_by = '" + ContractHeaderBackfill.CREATION_ACTOR + "'"))
                .isEqualTo(creationHeaders);
        assertThat(jdbc.queryForObject(
                "SELECT id FROM contracts WHERE id = ?", UUID.class, preexistingContractId))
                .isEqualTo(preexistingContractId);
        assertThat(count("SELECT count(*) FROM outbox_events")).isEqualTo(1L);
        assertThat(count("SELECT count(*) FROM amendment_document_lines")).isEqualTo(1L);
    }

    private static long insertSubscription(String suffix, String customerId, String status,
                                           LocalDate start, LocalDate end, boolean deleted) {
        boolean customerIsUuid = customerId.matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
        String subscriptionUuid = customerIsUuid ? customerId : UUID.randomUUID().toString();
        Long id = jdbc.queryForObject("""
                INSERT INTO subscriptions(uuid, subscription_number, customer_id, quote_id, status, plan_name,
                    start_date, end_date, is_deleted)
                VALUES (?, ?, ?, ?, ?, 'plan', ?, ?, ?)
                RETURNING id
                """, Long.class,
                subscriptionUuid,
                "SUB-" + suffix + "-" + subscriptionUuid.substring(0, 8),
                customerId,
                "Q-" + suffix,
                status,
                Date.valueOf(start),
                Date.valueOf(end),
                deleted);
        return id;
    }

    private static Map<String, Object> headerFor(String subscriptionUuid) {
        return jdbc.queryForMap("""
                SELECT c.contract_number, c.term_model, c.status, c.start_date, c.end_date, c.last_executed_seq,
                       c.created_by, c.tenant_id, c.customer_account_id, c.prior_contract_id,
                       c.governing_agreement_ref, s.contract_id
                FROM subscriptions s
                JOIN contracts c ON c.id = s.contract_id
                WHERE s.uuid = ?
                """, subscriptionUuid);
    }

    private static String statusOfBackfilled(String subscriptionStatus) {
        return jdbc.queryForObject("""
                SELECT c.status FROM subscriptions s
                JOIN contracts c ON c.id = s.contract_id
                WHERE s.status = ? AND c.created_by = ?
                """, String.class, subscriptionStatus, ContractHeaderBackfill.BACKFILL_ACTOR);
    }

    private static long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private static String latestFlywayVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1", String.class);
    }

    private static LocalDate date(Object value) {
        if (value instanceof LocalDate local) {
            return local;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        throw new IllegalStateException("Unexpected date " + value);
    }

    private static String schemaUrl() {
        String url = POSTGRES.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA;
    }

    private static void inTransaction(SqlWork work) throws Exception {
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    @FunctionalInterface
    private interface SqlWork {
        void run(Connection connection) throws Exception;
    }
}
