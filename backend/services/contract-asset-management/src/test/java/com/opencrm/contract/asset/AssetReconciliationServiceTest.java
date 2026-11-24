package com.opencrm.contract.asset;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DRG-902 AC3. Each delta kind, and an empty database, through {@link AssetReconciliationService#inventory}.
 * JDBC is scripted; the installed-base projection is the real one over an in-memory history store.
 */
class AssetReconciliationServiceTest {

    private static final LocalDate AS_OF = LocalDate.parse("2026-06-01");
    private static final LocalDate START = LocalDate.parse("2026-01-01");
    private static final LocalDate END = LocalDate.parse("2026-12-31");
    private static final String ACCOUNT = "acct-902";
    private static final String LINE = "11111111-1111-4111-8111-111111111111";
    private static final String ASSET = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";

    private JdbcTemplate jdbc;
    private InMemoryAssetHistoryStore history;
    private AssetHistoryProjection projection;
    private AssetReconciliationService service;
    private final List<Map<String, Object>> acceptances = new ArrayList<>();
    private final List<Map<String, Object>> assets = new ArrayList<>();
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final List<Map<String, Object>> contractLines = new ArrayList<>();
    private final List<Map<String, Object>> historyAccounts = new ArrayList<>();
    private final Map<String, Object> counts = new HashMap<>();

    @BeforeEach
    void setUp() throws SQLException {
        AssetReadTestCaller.signIn();
        jdbc = mock(JdbcTemplate.class);
        history = new InMemoryAssetHistoryStore();
        projection = new AssetHistoryProjection(history);
        service = new AssetReconciliationService(jdbc, projection);
        counts.put("acceptances", 0L);
        counts.put("rate_periods", 0L);
        counts.put("assets", 0L);
        counts.put("history_rows", 0L);
        counts.put("contract_lines", 0L);
        when(jdbc.queryForMap(anyString())).thenAnswer(invocation -> Map.copyOf(counts));
        when(jdbc.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            RowMapper<?> mapper = invocation.getArgument(1);
            List<Map<String, Object>> rows = rowsFor(sql);
            List<Object> mapped = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                mapped.add(mapper.mapRow(resultSet(rows.get(i)), i));
            }
            return mapped;
        });
    }

    @AfterEach
    void clearAuthorization() {
        AssetReadTestCaller.clear();
    }

    @Test
    void emptyDatabaseHasZeroDeltas() {
        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getAsOf()).isEqualTo(AS_OF);
        assertThat(report.getDeltas()).isEmpty();
        assertThat(report.getAcceptanceCount()).isZero();
        assertThat(report.getRatePeriodCount()).isZero();
        assertThat(report.getAssetCount()).isZero();
        assertThat(report.getAssetEventCount()).isZero();
        assertThat(report.getHistoryCount()).isZero();
        assertThat(report.getContractLineCount()).isZero();
        assertReadOnly();
    }

    @Test
    void nullAsOfIsRejected() {
        assertThatThrownBy(() -> service.inventory(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("asOf");
    }

    @Test
    void inventoryRunsInAReadOnlyTransaction() throws NoSuchMethodException {
        Transactional transactional = AssetReconciliationService.class
                .getMethod("inventory", LocalDate.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    void acceptanceWithoutAssetWhenTheLineIsVerified() {
        contractLines.add(line(LINE));
        acceptances.add(acceptance("quote-gap", 10L, LINE));
        counts.put("acceptances", 1L);
        counts.put("rate_periods", 1L);
        counts.put("contract_lines", 1L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).hasSize(1);
        AssetReconciliationDelta delta = report.getDeltas().get(0);
        assertThat(delta.getKind()).isEqualTo(AssetReconciliationDeltaKind.ACCEPTANCE_WITHOUT_ASSET);
        assertThat(delta.getSubscriptionId()).isEqualTo(10L);
        assertThat(delta.getContractLineId()).isEqualTo(LINE);
        assertThat(delta.getAssetUuid()).isNull();
        assertThat(delta.getDetail()).contains("quote-gap");
        assertThat(report.getAcceptanceCount()).isEqualTo(1L);
    }

    @Test
    void acceptanceWithNoLineDoesNotInventAnIdentity() {
        acceptances.add(acceptance("quote-blank", 11L, null));
        counts.put("acceptances", 1L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).hasSize(1);
        AssetReconciliationDelta delta = report.getDeltas().get(0);
        assertThat(delta.getKind()).isEqualTo(AssetReconciliationDeltaKind.UNVERIFIED_LINE_IDENTITY);
        assertThat(delta.getContractLineId()).isNull();
        assertThat(delta.getSubscriptionId()).isEqualTo(11L);
        assertThat(delta.getDetail()).containsIgnoringCase("not invented");
        assertThat(report.getDeltas())
                .extracting(AssetReconciliationDelta::getKind)
                .doesNotContain(AssetReconciliationDeltaKind.ACCEPTANCE_WITHOUT_ASSET);
    }

    @Test
    void assetWithoutCreatedEvent() {
        liveAsset(ASSET, LINE, 20L, "ACTIVE");
        verifiedHistory(ASSET, LINE, 20L);
        contractLines.add(line(LINE));
        counts.put("assets", 1L);
        counts.put("contract_lines", 1L);
        counts.put("history_rows", 1L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).hasSize(1);
        assertThat(report.getDeltas().get(0).getKind())
                .isEqualTo(AssetReconciliationDeltaKind.ASSET_WITHOUT_CREATED_EVENT);
        assertThat(report.getDeltas().get(0).getAssetUuid()).isEqualTo(ASSET);
        assertThat(report.getAssetEventCount()).isZero();
    }

    @Test
    void auditEventDoesNotCountAsAssetCreated() {
        liveAsset(ASSET, LINE, 21L, "ACTIVE");
        verifiedHistory(ASSET, LINE, 21L);
        contractLines.add(line(LINE));
        events.add(event("audit-1", ASSET, KafkaTopics.ASSET_AUDIT, KafkaTopics.ASSET_AUDIT));
        counts.put("assets", 1L);
        counts.put("contract_lines", 1L);
        counts.put("history_rows", 1L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).extracting(AssetReconciliationDelta::getKind)
                .containsExactly(AssetReconciliationDeltaKind.ASSET_WITHOUT_CREATED_EVENT);
        assertThat(report.getAssetEventCount()).isZero();
    }

    @Test
    void registryNameAssetCreatedCountsAsTheCreatedEvent() {
        liveAsset(ASSET, LINE, 22L, "ACTIVE");
        verifiedHistory(ASSET, LINE, 22L);
        contractLines.add(line(LINE));
        events.add(event("evt-created", ASSET, EventContractRegistry.ASSET_CREATED_V1.eventType(), null));
        counts.put("assets", 1L);
        counts.put("contract_lines", 1L);
        counts.put("history_rows", 1L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).isEmpty();
        assertThat(report.getAssetEventCount()).isEqualTo(1L);
    }

    @Test
    void orphanLifecycleEvent() {
        events.add(event("evt-orphan", "cccccccc-cccc-4ccc-8ccc-ccccccccccc3",
                KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_CREATED));

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).hasSize(1);
        AssetReconciliationDelta delta = report.getDeltas().get(0);
        assertThat(delta.getKind()).isEqualTo(AssetReconciliationDeltaKind.EVENT_WITHOUT_ASSET);
        assertThat(delta.getAssetUuid()).isEqualTo("cccccccc-cccc-4ccc-8ccc-ccccccccccc3");
        assertThat(delta.getDetail()).contains("evt-orphan").contains("AssetCreated");
        assertThat(report.getAssetEventCount()).isEqualTo(1L);
    }

    @Test
    void stateMismatchWhenTheRowWasClosedBeforeTheEffectiveDate() {
        liveAsset(ASSET, LINE, 30L, "TERMINATED");
        verifiedHistory(ASSET, LINE, 30L);
        AssetReportTestEvents.terminated(projection, "evt-future-term", ASSET, ACCOUNT,
                LocalDate.parse("2026-12-01"), "amd-1", "1");
        contractLines.add(line(LINE));
        events.add(event("evt-created", ASSET, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_CREATED));
        counts.put("assets", 1L);
        counts.put("contract_lines", 1L);
        counts.put("history_rows", 2L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).hasSize(1);
        AssetReconciliationDelta delta = report.getDeltas().get(0);
        assertThat(delta.getKind()).isEqualTo(AssetReconciliationDeltaKind.STATE_MISMATCH);
        assertThat(delta.getAssetUuid()).isEqualTo(ASSET);
        assertThat(delta.getDetail()).contains("TERMINATED").contains("ACTIVE");
    }

    @Test
    void terminalAssetAgreesWithTheProjection() {
        liveAsset(ASSET, LINE, 31L, "TERMINATED");
        verifiedHistory(ASSET, LINE, 31L);
        AssetReportTestEvents.terminated(projection, "evt-past-term", ASSET, ACCOUNT,
                LocalDate.parse("2026-05-01"), "amd-1", "1");
        contractLines.add(line(LINE));
        events.add(event("evt-created", ASSET, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_CREATED));
        counts.put("assets", 1L);
        counts.put("contract_lines", 1L);
        counts.put("history_rows", 2L);

        assertThat(service.inventory(AS_OF).getDeltas()).isEmpty();
    }

    @Test
    void successorLineIsUnverified() {
        String successor = LINE + ":successor";
        liveAsset(ASSET, successor, 40L, "ACTIVE");
        verifiedHistory(ASSET, successor, 40L);
        events.add(event("evt-created", ASSET, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_CREATED));
        counts.put("assets", 1L);
        counts.put("history_rows", 1L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).hasSize(1);
        AssetReconciliationDelta delta = report.getDeltas().get(0);
        assertThat(delta.getKind()).isEqualTo(AssetReconciliationDeltaKind.UNVERIFIED_LINE_IDENTITY);
        assertThat(delta.getContractLineId()).isEqualTo(successor);
        assertThat(delta.getAssetUuid()).isEqualTo(ASSET);
        assertThat(delta.getDetail()).contains(":successor");
    }

    @Test
    void unmatchedContractLineIsUnverified() {
        liveAsset(ASSET, "line-not-in-contract", 41L, "ACTIVE");
        verifiedHistory(ASSET, "line-not-in-contract", 41L);
        events.add(event("evt-created", ASSET, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_CREATED));
        counts.put("assets", 1L);
        counts.put("history_rows", 1L);

        AssetReconciliationDelta delta = service.inventory(AS_OF).getDeltas().get(0);

        assertThat(delta.getKind()).isEqualTo(AssetReconciliationDeltaKind.UNVERIFIED_LINE_IDENTITY);
        assertThat(delta.getContractLineId()).isEqualTo("line-not-in-contract");
        assertThat(delta.getDetail()).contains("contract_lines");
    }

    @Test
    void consistentAssetHasNoDelta() {
        liveAsset(ASSET, LINE, 50L, "ACTIVE");
        verifiedHistory(ASSET, LINE, 50L);
        contractLines.add(line(LINE));
        events.add(event("evt-created", ASSET, KafkaTopics.ASSET_CREATED, KafkaTopics.ASSET_CREATED));
        counts.put("assets", 1L);
        counts.put("contract_lines", 1L);
        counts.put("history_rows", 1L);

        AssetReconciliationReport report = service.inventory(AS_OF);

        assertThat(report.getDeltas()).isEmpty();
        assertThat(report.getAssetCount()).isEqualTo(1L);
        assertThat(report.getHistoryCount()).isEqualTo(1L);
        assertReadOnly();
    }

    private void assertReadOnly() {
        assertThat(Mockito.mockingDetails(jdbc).getInvocations())
                .extracting(invocation -> invocation.getMethod().getName())
                .doesNotContain("update", "batchUpdate", "execute");
    }

    private void liveAsset(String uuid, String line, long subscriptionId, String status) {
        Map<String, Object> row = new HashMap<>();
        row.put("uuid", uuid);
        row.put("account_id", ACCOUNT);
        row.put("contract_line_id", line);
        row.put("subscription_id", subscriptionId);
        row.put("product_ref", "SKU");
        row.put("quantity", new BigDecimal("1.0000"));
        row.put("status", status);
        row.put("effective_start", START);
        row.put("effective_end", END);
        row.put("is_deleted", false);
        assets.add(row);
    }

    private void verifiedHistory(String assetId, String line, long subscriptionId) {
        projection.apply(new AssetCreatedEvent(
                assetId, ACCOUNT, line, subscriptionId, null, "SKU", new BigDecimal("1.0000"),
                "ACTIVE", START, END), "hist-" + assetId);
        historyAccounts.add(Map.of("account_id", ACCOUNT));
    }

    private static Map<String, Object> acceptance(String quoteId, long subscriptionId, String line) {
        Map<String, Object> row = new HashMap<>();
        row.put("accepted_quote_id", quoteId);
        row.put("subscription_id", subscriptionId);
        row.put("accepted_line_uuid", line);
        return row;
    }

    private static Map<String, Object> line(String acceptedLineUuid) {
        return Map.of("accepted_line_uuid", acceptedLineUuid);
    }

    private static Map<String, Object> event(String uuid, String aggregateId, String eventType, String topic) {
        Map<String, Object> row = new HashMap<>();
        row.put("uuid", uuid);
        row.put("aggregate_id", aggregateId);
        row.put("event_type", eventType);
        row.put("topic", topic);
        return row;
    }

    private List<Map<String, Object>> rowsFor(String sql) {
        if (sql.contains("FROM applied_quote_acceptances")) {
            return acceptances;
        }
        if (sql.contains("FROM assets")) {
            return assets;
        }
        if (sql.contains("FROM outbox_events")) {
            return events;
        }
        if (sql.contains("FROM contract_lines")) {
            return contractLines;
        }
        if (sql.contains("FROM asset_history")) {
            return historyAccounts;
        }
        return List.of();
    }

    private static ResultSet resultSet(Map<String, Object> values) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(anyString())).thenAnswer(invocation -> {
            Object value = values.get(invocation.getArgument(0));
            return value == null ? null : value.toString();
        });
        when(rs.getBoolean(anyString())).thenAnswer(invocation ->
                Boolean.TRUE.equals(values.get(invocation.getArgument(0))));
        when(rs.getBigDecimal(anyString())).thenAnswer(invocation ->
                (BigDecimal) values.get(invocation.getArgument(0)));
        when(rs.getObject(anyString(), org.mockito.ArgumentMatchers.eq(Long.class))).thenAnswer(invocation -> {
            Object value = values.get(invocation.getArgument(0));
            return value == null ? null : ((Number) value).longValue();
        });
        when(rs.getObject(anyString(), org.mockito.ArgumentMatchers.eq(LocalDate.class))).thenAnswer(invocation ->
                values.get(invocation.getArgument(0)));
        return rs;
    }
}
