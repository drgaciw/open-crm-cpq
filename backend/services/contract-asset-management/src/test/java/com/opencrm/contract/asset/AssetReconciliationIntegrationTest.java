package com.opencrm.contract.asset;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.contract.ContractAssetManagementApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-902 AC3. Seeds the unexplained cases on Postgres 16 and proves the inventory writes nothing.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class AssetReconciliationIntegrationTest {

    private static final LocalDate AS_OF = LocalDate.parse("2026-06-01");
    private static final String LINE_GAP = "11111111-1111-4111-8111-111111111111";
    private static final String LINE_LEGACY = "22222222-2222-4222-8222-222222222222";
    private static final String LINE_MISMATCH = "55555555-5555-4555-8555-555555555555";
    private static final String LINE_CLEAN = "66666666-6666-4666-8666-666666666666";
    private static final String SUCCESSOR_LINE = "44444444-4444-4444-8444-444444444444:successor";
    private static final String LEGACY_ASSET = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String ORPHAN_ASSET = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3";
    private static final String SUCCESSOR_ASSET = "dddddddd-dddd-4ddd-8ddd-ddddddddddd4";
    private static final String MISMATCH_ASSET = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5";
    private static final String CLEAN_ASSET = "ffffffff-ffff-4fff-8fff-fffffffffff6";
    private static final String DIGEST = "a".repeat(64);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> {
            String url = POSTGRES.getJdbcUrl();
            return url + (url.contains("?") ? "&" : "?") + "currentSchema=contract";
        });
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired AssetReconciliationService inventory;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void authorizeRead() {
        AssetReadTestCaller.signIn();
    }

    @AfterEach
    void clearReadAuthorization() {
        AssetReadTestCaller.clear();
    }

    @Test
    void seededCasesAreReportedAndRowCountsStayEqual() {
        AssetReconciliationReport empty = inventory.inventory(AS_OF);
        assertThat(empty.getDeltas()).isEmpty();
        assertThat(empty.getAcceptanceCount()).isZero();
        assertThat(empty.getRatePeriodCount()).isZero();
        assertThat(empty.getAssetCount()).isZero();
        assertThat(empty.getAssetEventCount()).isZero();
        assertThat(empty.getHistoryCount()).isZero();
        assertThat(empty.getContractLineCount()).isZero();

        UUID contractId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        insertContract(contractId, tenantId);
        insertContractLine(contractId, tenantId, 1, LINE_GAP);
        insertContractLine(contractId, tenantId, 2, LINE_LEGACY);
        insertContractLine(contractId, tenantId, 3, LINE_MISMATCH);
        insertContractLine(contractId, tenantId, 4, LINE_CLEAN);

        long gapSubscription = insertSubscription("SUB-GAP");
        insertAcceptance(gapSubscription, "quote-gap");
        insertRatePeriod(gapSubscription, LINE_GAP);

        long legacySubscription = insertSubscription("SUB-LEGACY");
        insertAsset(LEGACY_ASSET, "acct-legacy", LINE_LEGACY, legacySubscription, "ACTIVE");
        insertHistory("hist-legacy", LEGACY_ASSET, "acct-legacy", "CREATED", "2026-01-01",
                LINE_LEGACY, legacySubscription, 1);

        insertOutbox("evt-orphan", ORPHAN_ASSET, KafkaTopics.ASSET_CREATED);

        long successorSubscription = insertSubscription("SUB-SUCCESSOR");
        insertAsset(SUCCESSOR_ASSET, "acct-successor", SUCCESSOR_LINE, successorSubscription, "ACTIVE");
        insertHistory("hist-successor", SUCCESSOR_ASSET, "acct-successor", "CREATED", "2026-01-01",
                SUCCESSOR_LINE, successorSubscription, 1);
        insertOutbox("evt-successor", SUCCESSOR_ASSET, KafkaTopics.ASSET_CREATED);

        long mismatchSubscription = insertSubscription("SUB-MISMATCH");
        insertAsset(MISMATCH_ASSET, "acct-mismatch", LINE_MISMATCH, mismatchSubscription, "TERMINATED");
        insertHistory("hist-mismatch", MISMATCH_ASSET, "acct-mismatch", "CREATED", "2026-01-01",
                LINE_MISMATCH, mismatchSubscription, 1);
        insertHistory("hist-mismatch-term", MISMATCH_ASSET, "acct-mismatch", "TERMINATED", "2026-12-01",
                LINE_MISMATCH, mismatchSubscription, 2);
        insertOutbox("evt-mismatch", MISMATCH_ASSET, KafkaTopics.ASSET_CREATED);

        long cleanSubscription = insertSubscription("SUB-CLEAN");
        insertAsset(CLEAN_ASSET, "acct-clean", LINE_CLEAN, cleanSubscription, "ACTIVE");
        insertHistory("hist-clean", CLEAN_ASSET, "acct-clean", "CREATED", "2026-01-01",
                LINE_CLEAN, cleanSubscription, 1);
        insertOutbox("evt-clean", CLEAN_ASSET, KafkaTopics.ASSET_CREATED);

        Map<String, Long> before = tableCounts();
        assertThat(before).containsKeys(
                "applied_quote_acceptances",
                "subscription_rate_periods",
                "contract_lines",
                "assets",
                "asset_history",
                "outbox_events");

        AssetReconciliationReport report = inventory.inventory(AS_OF);

        assertThat(tableCounts()).isEqualTo(before);
        assertThat(report.getAcceptanceCount()).isEqualTo(1L);
        assertThat(report.getRatePeriodCount()).isEqualTo(1L);
        assertThat(report.getAssetCount()).isEqualTo(4L);
        assertThat(report.getAssetEventCount()).isEqualTo(4L);
        assertThat(report.getHistoryCount()).isEqualTo(5L);
        assertThat(report.getContractLineCount()).isEqualTo(4L);

        assertThat(ofKind(report, AssetReconciliationDeltaKind.ACCEPTANCE_WITHOUT_ASSET))
                .singleElement()
                .satisfies(delta -> {
                    assertThat(delta.getSubscriptionId()).isEqualTo(gapSubscription);
                    assertThat(delta.getContractLineId()).isEqualTo(LINE_GAP);
                    assertThat(delta.getAssetUuid()).isNull();
                });
        assertThat(ofKind(report, AssetReconciliationDeltaKind.ASSET_WITHOUT_CREATED_EVENT))
                .singleElement()
                .extracting(AssetReconciliationDelta::getAssetUuid)
                .isEqualTo(LEGACY_ASSET);
        assertThat(ofKind(report, AssetReconciliationDeltaKind.EVENT_WITHOUT_ASSET))
                .singleElement()
                .satisfies(delta -> {
                    assertThat(delta.getAssetUuid()).isEqualTo(ORPHAN_ASSET);
                    assertThat(delta.getDetail()).contains("AssetCreated");
                });
        assertThat(ofKind(report, AssetReconciliationDeltaKind.STATE_MISMATCH))
                .singleElement()
                .satisfies(delta -> {
                    assertThat(delta.getAssetUuid()).isEqualTo(MISMATCH_ASSET);
                    assertThat(delta.getDetail()).contains("TERMINATED").contains("ACTIVE");
                });
        assertThat(ofKind(report, AssetReconciliationDeltaKind.UNVERIFIED_LINE_IDENTITY))
                .singleElement()
                .satisfies(delta -> {
                    assertThat(delta.getAssetUuid()).isEqualTo(SUCCESSOR_ASSET);
                    assertThat(delta.getContractLineId()).isEqualTo(SUCCESSOR_LINE);
                    assertThat(delta.getDetail()).contains(":successor");
                });
        assertThat(report.getDeltas()).extracting(AssetReconciliationDelta::getAssetUuid)
                .doesNotContain(CLEAN_ASSET);
        assertThat(report.getDeltas()).hasSize(5);
    }

    private static List<AssetReconciliationDelta> ofKind(AssetReconciliationReport report,
                                                         AssetReconciliationDeltaKind kind) {
        return report.getDeltas().stream().filter(delta -> delta.getKind() == kind).toList();
    }

    private Map<String, Long> tableCounts() {
        List<String> tables = jdbc.queryForList("""
                SELECT c.relname
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = current_schema()
                  AND c.relkind = 'r'
                  AND c.relname NOT LIKE 'flyway%'
                ORDER BY 1
                """, String.class);
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : tables) {
            if (!table.matches("[a-z0-9_]+")) {
                throw new IllegalStateException("unexpected table " + table);
            }
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }

    private void insertContract(UUID contractId, UUID tenantId) {
        jdbc.update("""
                INSERT INTO contracts (
                    id, tenant_id, contract_number, customer_account_id, term_model,
                    start_date, end_date, status, last_executed_seq, created_by, version)
                VALUES (?, ?, ?, ?, 'TERMED', DATE '2026-01-01', DATE '2026-12-31',
                        'ACTIVE', 0, '902-r', 0)
                """, contractId, tenantId, "C-902R-" + contractId.toString().substring(0, 8), UUID.randomUUID());
    }

    private void insertContractLine(UUID contractId, UUID tenantId, int lineNo, String acceptedLineUuid) {
        jdbc.update("""
                INSERT INTO contract_lines (
                    id, tenant_id, contract_id, line_no, accepted_line_uuid, product_external_id,
                    quantity, status, effective_from, charge_type, created_by, version)
                VALUES (?, ?, ?, ?, ?, 'SKU', 1.0000, 'ACTIVE', DATE '2026-01-01',
                        'RECURRING', '902-r', 0)
                """, UUID.randomUUID(), tenantId, contractId, lineNo, acceptedLineUuid);
    }

    /**
     * The installed-base projection is tenant-scoped through {@code subscriptions.tenant_id} (DRG-906, #1077): it
     * shows only assets of the signed-in caller's tenant. Seed subscriptions under that tenant so the projection
     * sees the fixture data.
     */
    private long insertSubscription(String number) {
        Long id = jdbc.queryForObject("""
                INSERT INTO subscriptions(
                    uuid, subscription_number, customer_id, quote_id, plan_name,
                    start_date, end_date, status, currency_code, commercial_revision, tenant_id)
                VALUES (?, ?, 'customer', 'quote', 'Plan', DATE '2026-01-01', DATE '2026-12-31',
                        'ACTIVE', 'USD', 0, ?)
                RETURNING id
                """, Long.class, UUID.randomUUID().toString(), number, UUID.fromString(AssetReadTestCaller.TENANT));
        if (id == null) {
            throw new IllegalStateException("subscription id");
        }
        return id;
    }

    private void insertAcceptance(long subscriptionId, String quoteId) {
        jdbc.update("""
                INSERT INTO applied_quote_acceptances(
                    uuid, accepted_quote_id, subscription_id, operation, commercial_digest)
                VALUES (?, ?, ?, 'ACCEPT', ?)
                """, UUID.randomUUID().toString(), quoteId, subscriptionId, DIGEST);
    }

    private void insertRatePeriod(long subscriptionId, String acceptedLineUuid) {
        jdbc.update("""
                INSERT INTO subscription_rate_periods(
                    uuid, subscription_id, accepted_line_uuid, sequence,
                    start_date, billing_anchor_date, end_date, effective_end_date, provenance)
                VALUES (?, ?, ?, 1, DATE '2026-01-01', DATE '2026-01-01', DATE '2026-12-31',
                        DATE '2026-12-31', 'QUOTE')
                """, UUID.randomUUID().toString(), subscriptionId, acceptedLineUuid);
    }

    private void insertAsset(String uuid, String accountId, String contractLineId,
                             long subscriptionId, String status) {
        jdbc.update("""
                INSERT INTO assets(
                    uuid, account_id, contract_line_id, subscription_id, product_ref, quantity,
                    status, effective_start, effective_end)
                VALUES (?, ?, ?, ?, 'SKU', 1.0000, ?, DATE '2026-01-01', DATE '2026-12-31')
                """, uuid, accountId, contractLineId, subscriptionId, status);
    }

    private void insertHistory(String eventId, String assetId, String accountId, String eventType,
                               String effectiveDate, String contractLineId, long subscriptionId, int order) {
        jdbc.update("""
                INSERT INTO asset_history(
                    event_id, asset_id, account_id, event_type, effective_date, effective_end,
                    contract_line_id, subscription_id, product_ref, quantity, applied_order)
                VALUES (?, ?, ?, ?, ?::date, DATE '2026-12-31', ?, ?, 'SKU', 1.0000, ?)
                """, eventId, assetId, accountId, eventType, effectiveDate, contractLineId, subscriptionId, order);
    }

    private void insertOutbox(String uuid, String aggregateId, String topic) {
        jdbc.update("""
                INSERT INTO outbox_events(
                    uuid, aggregate_type, aggregate_id, event_type, topic, payload)
                VALUES (?, 'Asset', ?, ?, ?, '{}')
                """, uuid, aggregateId, topic, topic);
    }
}
