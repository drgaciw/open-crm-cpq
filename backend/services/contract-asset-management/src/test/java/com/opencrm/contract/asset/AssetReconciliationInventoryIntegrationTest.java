package com.opencrm.contract.asset;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Testcontainers
class AssetReconciliationInventoryIntegrationTest {
    private static final String SCHEMA = "cam_pilot";
    private static final String READER = "cam_inventory";
    private static final String SCRIPT = "/tmp/asset-reconciliation.sql";
    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
    private static final LocalDate START = TODAY.minusMonths(1);
    private static final LocalDate END = TODAY.plusMonths(1);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static Origin missingLine;
    private static Origin missingReceipt;
    private static Origin coveredOrigin;
    private static Origin foreignAccount;
    private static UUID approvedAsset;
    private static UUID legacyAsset;
    private static UUID conflictingAsset;
    private static UUID extraQuoteLine;
    private static UUID futureReceipt;
    private static UUID dueReceipt;
    private static UUID flaggedReceipt;
    private static UUID historyMissingReceipt;
    private static UUID elapsedReceipt;
    private static UUID pendingOutbox;
    private static UUID publishedOutbox;

    @BeforeAll
    static void seedPilot() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl() + "&currentSchema=" + SCHEMA,
                POSTGRES.getUsername(), POSTGRES.getPassword()));

        missingLine = origin(false);
        missingReceipt = origin(true);
        coveredOrigin = origin(true);
        foreignAccount = origin(true);
        jdbc.update("update subscriptions set customer_account_id = ? where id = ?",
                UUID.randomUUID(), foreignAccount.id());

        approve(coveredOrigin);
        extraQuoteLine = UUID.randomUUID();
        rate(coveredOrigin, extraQuoteLine, 1);
        rate(coveredOrigin, extraQuoteLine, 2);
        rate(coveredOrigin, coveredOrigin.quoteLine(), 1);

        legacyAsset = asset(missingLine, "legacy-aggregate", "product-1");
        conflictingAsset = asset(missingReceipt, missingReceipt.line().toString(), "other-product");
        futureReceipt = receipt(origin(true), "PENDING", TODAY.plusDays(1), null, null, null);
        dueReceipt = receipt(origin(true), "PENDING", TODAY, null, null, null);
        flaggedReceipt = receipt(origin(true), "NEEDS_RECONCILIATION", START, null, null, "PREEXISTING_ASSET");
        elapsedReceipt = receipt(origin(true), "PENDING", START, null, null, null);
        jdbc.update("update asset_activation_receipts set effective_end = ? where activation_id = ?",
                TODAY.minusDays(1), elapsedReceipt);
        Origin historyMissing = origin(true);
        historyMissingReceipt = receipt(historyMissing, "APPLIED", START,
                asset(historyMissing, historyMissing.line().toString(), "product-1"), UUID.randomUUID(), null);

        // Pre-envelope legacy rows used event_type as the topic.
        pendingOutbox = outbox(legacyAsset, "PENDING", null, "cpq.asset.created");
        publishedOutbox = outbox(legacyAsset, "PUBLISHED", "cpq.asset.created", "AssetCreated");

        jdbc.execute("create role cam_inventory login password 'cam-pilot-reader'");
        jdbc.execute("grant usage on schema cam_pilot to cam_inventory");
        jdbc.execute("grant select on all tables in schema cam_pilot to cam_inventory");
    }

    @Test
    void inventoryShowsUnprovenAcceptancesDeduplicatesRampLinesAndFlagsConflictingAssets() throws Exception {
        List<String> rows = report(SCHEMA);
        assertThat(rows).anyMatch(row -> row.startsWith("ACCEPTANCE,CANONICAL_LINE_MISSING," + missingLine.id() + ","));
        assertThat(rows).anyMatch(row -> row.startsWith("ACCEPTANCE,ACTIVATION_RECEIPT_MISSING," + missingReceipt.id() + ","));
        assertThat(rows).anyMatch(row -> row.startsWith("ACCEPTANCE,CUSTOMER_ACCOUNT_MISMATCH," + foreignAccount.id() + ","));
        assertThat(rows.stream().filter(row -> row.contains(extraQuoteLine.toString())).toList())
                .singleElement().asString().startsWith("ACCEPTED_LINE,ACCEPTED_LINE_WITHOUT_CANONICAL_PROOF,");
        assertThat(rows).anyMatch(row -> row.startsWith("ASSET,LEGACY_ASSET_LINE_ID,")
                && row.contains(legacyAsset.toString()));
        assertThat(rows).anyMatch(row -> row.startsWith("ASSET,ASSET_PRODUCT_MISMATCH,")
                && row.contains(conflictingAsset.toString()));
        assertThat(rows).noneMatch(row -> row.contains(approvedAsset.toString()));
        assertThat(rows).noneMatch(row -> row.startsWith("ACCEPTANCE,") && row.contains("," + coveredOrigin.id() + ","));
    }

    @Test
    void inventorySeparatesFutureDueAndUnresolvedReceiptsAndRetainedUnpublishedEvents() throws Exception {
        List<String> rows = report(SCHEMA);
        assertThat(rows).anyMatch(row -> row.startsWith("RECEIPT,ACTIVATION_PENDING_FUTURE,")
                && row.contains(futureReceipt.toString()));
        assertThat(rows).anyMatch(row -> row.startsWith("RECEIPT,ACTIVATION_PENDING_DUE,")
                && row.contains(dueReceipt.toString()));
        assertThat(rows).anyMatch(row -> row.startsWith("RECEIPT,ACTIVATION_PENDING_ELAPSED,")
                && row.contains(elapsedReceipt.toString()));
        assertThat(rows).anyMatch(row -> row.startsWith("RECEIPT,ACTIVATION_HISTORY_UNPROVEN,")
                && row.contains(historyMissingReceipt.toString()));
        assertThat(rows).anyMatch(row -> row.startsWith("RECEIPT,ACTIVATION_NEEDS_RECONCILIATION,")
                && row.contains(flaggedReceipt.toString()) && row.contains("PREEXISTING_ASSET"));
        assertThat(rows).anyMatch(row -> row.startsWith("OUTBOX,ASSET_OUTBOX_NOT_PUBLISHED,")
                && row.contains(pendingOutbox.toString()));
        assertThat(rows).noneMatch(row -> row.contains(publishedOutbox.toString()));
    }

    @Test
    void selectOnlyOperatorCanRepeatTheSnapshotWithoutChangingEvidence() throws Exception {
        assertThat(jdbc.queryForObject("select has_table_privilege('cam_inventory', 'cam_pilot.assets', 'UPDATE')",
                Boolean.class)).isFalse();
        List<String> first = report(SCHEMA);
        assertThat(report(SCHEMA)).containsExactlyElementsOf(first);
        assertThat(jdbc.queryForObject("select status from asset_activation_receipts where activation_id = ?",
                String.class, dueReceipt)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select status from outbox_events where uuid = ?",
                String.class, pendingOutbox.toString())).isEqualTo("PENDING");
    }

    @Test
    void wrongSchemaFailsInsteadOfReadingAnotherSchema() throws Exception {
        ExecResult result = runInventory("schema_missing");
        assertThat(result.getExitCode()).isNotZero();
        assertThat(result.getStdout()).isBlank();
        assertThat(result.getStderr()).contains("does not exist");
    }

    private static List<String> report(String schema) throws Exception {
        ExecResult result = runInventory(schema);
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        return result.getStdout().lines().toList();
    }

    private static ExecResult runInventory(String schema) throws Exception {
        Path path = Path.of("ops", "asset-reconciliation.sql").toAbsolutePath();
        assertThat(Files.isRegularFile(path)).as("operator inventory SQL exists").isTrue();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(path), SCRIPT);
        return POSTGRES.execInContainer("env", "PGPASSWORD=cam-pilot-reader", "psql", "-X", "--quiet", "--csv",
                "-h", "127.0.0.1", "-U", READER, "-d", POSTGRES.getDatabaseName(),
                "-v", "cam_schema=" + schema, "-f", SCRIPT);
    }

    private static Origin origin(boolean linked) {
        UUID tenant = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        UUID contract = linked ? UUID.randomUUID() : null;
        UUID line = linked ? UUID.randomUUID() : null;
        String quote = UUID.randomUUID().toString();
        UUID quoteLine = UUID.randomUUID();
        String customer = "pilot-only-" + account;
        if (linked) {
            jdbc.update("""
                    insert into contracts (id, tenant_id, contract_number, customer_account_id, term_model,
                        start_date, end_date, status, created_by)
                    values (?, ?, ?, ?, 'TERMED', ?, ?, 'ACTIVE', 'pilot')
                    """, contract, tenant, contract.toString(), account, START, END);
            jdbc.update("""
                    insert into contract_lines (id, tenant_id, contract_id, line_no, accepted_line_uuid, quote_id,
                        product_external_id, quantity, status, effective_from, effective_to, charge_type, created_by)
                    values (?, ?, ?, 1, ?, ?, 'product-1', 3, 'ACTIVE', ?, ?, 'RECURRING', 'pilot')
                    """, line, tenant, contract, quoteLine.toString(), quote, START, END);
        }
        Long id = jdbc.queryForObject("""
                insert into subscriptions (uuid, subscription_number, customer_id, quote_id, status, plan_name,
                    start_date, end_date, contract_id, contract_line_id, customer_account_id, tenant_id)
                values (?, ?, ?, ?, 'ACTIVE', 'Pilot', ?, ?, ?, ?, ?, ?) returning id
                """, Long.class, UUID.randomUUID().toString(), "PILOT-" + quote, customer, quote, START, END,
                contract, line, linked ? account : null, linked ? tenant : null);
        jdbc.update("""
                insert into applied_quote_acceptances (uuid, accepted_quote_id, subscription_id, operation, commercial_digest)
                values (?, ?, ?, 'NEW_SALE', repeat('0', 64))
                """, UUID.randomUUID().toString(), quote, id);
        return new Origin(id, tenant, account, contract, line, quote, quoteLine, customer);
    }

    private static void rate(Origin origin, UUID line, int sequence) {
        jdbc.update("""
                insert into subscription_rate_periods (uuid, subscription_id, accepted_line_uuid, accepted_quote_id,
                    product_external_id, sequence, start_date, billing_anchor_date, end_date, effective_end_date, provenance)
                values (?, ?, ?, ?, 'product-1', ?, ?, ?, ?, ?, 'ACCEPTED_QUOTE')
                """, UUID.randomUUID().toString(), origin.id(), line.toString(), origin.quote(), sequence,
                START, START, END, END);
    }

    private static void approve(Origin origin) {
        approvedAsset = asset(origin, origin.line().toString(), "product-1");
        UUID event = UUID.randomUUID();
        receipt(origin, "APPLIED", START, approvedAsset, event, null);
        jdbc.update("""
                insert into asset_history (event_id, asset_id, account_id, event_type, effective_date, effective_end,
                    contract_line_id, subscription_id, product_ref, quantity, applied_order)
                values (?, ?, ?, 'CREATED', ?, ?, ?, ?, 'product-1', 3, 1)
                """, event.toString(), approvedAsset.toString(), origin.customer(), START, END, origin.line().toString(),
                origin.id());
    }

    private static UUID asset(Origin origin, String assetLine, String product) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into assets (uuid, account_id, contract_line_id, subscription_id, product_ref, quantity,
                    status, effective_start, effective_end)
                values (?, ?, ?, ?, ?, 3, 'ACTIVE', ?, ?)
                """, id.toString(), origin.customer(), assetLine, origin.id(), product, START, END);
        return id;
    }

    private static UUID receipt(Origin origin, String status, LocalDate effective, UUID asset, UUID event, String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into asset_activation_receipts (activation_id, contract_line_id, subscription_id, contract_id,
                    tenant_id, customer_account_id, external_customer_id, quote_id, accepted_line_uuid, product_ref,
                    quantity, activated_on, effective_start, effective_end, status, asset_id, created_event_id,
                    reconciliation_reason, processed_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'product-1', 3, ?, ?, ?, ?, ?, ?, ?,
                    case when ? = 'PENDING' then null else now() end)
                """, id, origin.line(), origin.id(), origin.contract(), origin.tenant(), origin.account(),
                origin.customer(), origin.quote(), origin.quoteLine().toString(), START, effective, END, status,
                asset == null ? null : asset.toString(), event == null ? null : event.toString(), reason, status);
        return id;
    }

    private static UUID outbox(UUID asset, String status, String topic, String eventType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into outbox_events (uuid, aggregate_type, aggregate_id, event_type, topic, payload, status, published)
                values (?, 'Asset', ?, ?, ?, '{}', ?, ?)
                """, id.toString(), asset.toString(), eventType, topic, status, "PUBLISHED".equals(status));
        return id;
    }

    private record Origin(long id, UUID tenant, UUID account, UUID contract, UUID line, String quote,
                          UUID quoteLine, String customer) {
    }
}
