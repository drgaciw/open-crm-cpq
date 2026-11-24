package com.opencrm.contract.asset;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.KafkaTopics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Read-only inventory of acceptances, asset rows, and published asset events (DRG-902 AC3).
 * No endpoint and no scheduler. Identities come only from stored columns.
 */
@Service
public class AssetReconciliationService {

    static final String COUNTS_SQL = """
            SELECT
              (SELECT COUNT(*) FROM applied_quote_acceptances WHERE is_deleted = FALSE) AS acceptances,
              (SELECT COUNT(*) FROM subscription_rate_periods WHERE is_deleted = FALSE) AS rate_periods,
              (SELECT COUNT(*) FROM assets WHERE is_deleted = FALSE) AS assets,
              (SELECT COUNT(*) FROM asset_history) AS history_rows,
              (SELECT COUNT(*) FROM contract_lines) AS contract_lines
            """;

    static final String ACCEPTANCES_SQL = """
            SELECT a.accepted_quote_id, a.subscription_id, p.accepted_line_uuid
            FROM applied_quote_acceptances a
            LEFT JOIN subscription_rate_periods p
              ON p.subscription_id = a.subscription_id
             AND p.is_deleted = FALSE
            WHERE a.is_deleted = FALSE
            """;

    static final String ASSETS_SQL = """
            SELECT uuid, account_id, contract_line_id, subscription_id, product_ref, quantity,
                   status, effective_start, effective_end, is_deleted
            FROM assets
            """;

    static final String EVENTS_SQL = """
            SELECT uuid, aggregate_id, event_type, topic
            FROM outbox_events
            WHERE is_deleted = FALSE
              AND aggregate_type = 'Asset'
            """;

    static final String CONTRACT_LINES_SQL = """
            SELECT accepted_line_uuid
            FROM contract_lines
            """;

    static final String HISTORY_ACCOUNTS_SQL = """
            SELECT DISTINCT account_id
            FROM asset_history
            """;

    private static final String SUCCESSOR_SUFFIX = ":successor";

    private final JdbcTemplate jdbc;
    private final AssetHistoryProjection projection;

    public AssetReconciliationService(JdbcTemplate jdbc, AssetHistoryProjection projection) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.projection = Objects.requireNonNull(projection, "projection");
    }

    @Transactional(readOnly = true)
    public AssetReconciliationReport inventory(LocalDate asOf) {
        if (asOf == null) {
            throw new IllegalArgumentException("asOf is required");
        }
        Map<String, Object> counts = jdbc.queryForMap(COUNTS_SQL);
        List<AcceptanceView> acceptances = jdbc.query(ACCEPTANCES_SQL, this::mapAcceptance);
        List<AssetView> assets = jdbc.query(ASSETS_SQL, this::mapAsset);
        List<EventView> events = jdbc.query(EVENTS_SQL, this::mapEvent);
        Set<String> verifiedLines = new HashSet<>();
        for (String line : jdbc.query(CONTRACT_LINES_SQL, (rs, rowNum) -> rs.getString("accepted_line_uuid"))) {
            if (line != null) {
                verifiedLines.add(line);
            }
        }
        Set<String> accounts = new LinkedHashSet<>();
        for (AssetView asset : assets) {
            if (!asset.deleted() && !blank(asset.accountId())) {
                accounts.add(asset.accountId());
            }
        }
        for (String account : jdbc.query(HISTORY_ACCOUNTS_SQL, (rs, rowNum) -> rs.getString("account_id"))) {
            if (!blank(account)) {
                accounts.add(account);
            }
        }

        List<EventView> lifecycle = events.stream().filter(AssetReconciliationService::isLifecycle).toList();
        Set<String> knownAssets = new HashSet<>();
        Set<String> storedKeys = new HashSet<>();
        Set<String> liveKeys = new HashSet<>();
        for (AssetView asset : assets) {
            if (!blank(asset.uuid())) {
                knownAssets.add(asset.uuid());
            }
            storedKeys.add(lineKey(asset.subscriptionId(), asset.contractLineId()));
            if (!asset.deleted()) {
                liveKeys.add(lineKey(asset.subscriptionId(), asset.contractLineId()));
            }
        }
        Set<String> createdFor = new HashSet<>();
        for (EventView event : lifecycle) {
            if (isCreated(event) && !blank(event.aggregateId())) {
                createdFor.add(event.aggregateId());
            }
        }
        Map<String, InstalledAssetDto> projected = project(accounts, asOf);

        List<AssetReconciliationDelta> deltas = new ArrayList<>();
        deltas.addAll(acceptanceDeltas(acceptances, storedKeys, liveKeys, verifiedLines));
        deltas.addAll(assetDeltas(assets, createdFor, verifiedLines, projected, asOf));
        deltas.addAll(eventDeltas(lifecycle, knownAssets));
        deltas.addAll(projectionOnlyDeltas(projected, assets, asOf));
        deltas.sort(DELTA_ORDER);

        return AssetReconciliationReport.builder()
                .asOf(asOf)
                .acceptanceCount(count(counts, "acceptances"))
                .ratePeriodCount(count(counts, "rate_periods"))
                .assetCount(count(counts, "assets"))
                .assetEventCount(lifecycle.size())
                .historyCount(count(counts, "history_rows"))
                .contractLineCount(count(counts, "contract_lines"))
                .deltas(List.copyOf(deltas))
                .build();
    }

    private Map<String, InstalledAssetDto> project(Set<String> accounts, LocalDate asOf) {
        Map<String, InstalledAssetDto> projected = new HashMap<>();
        for (String account : accounts) {
            for (InstalledAssetDto installed : projection.installedBase(account, asOf)) {
                if (installed != null && !blank(installed.getAssetId())) {
                    projected.putIfAbsent(installed.getAssetId(), installed);
                }
            }
        }
        return projected;
    }

    private static List<AssetReconciliationDelta> acceptanceDeltas(List<AcceptanceView> acceptances,
                                                                  Set<String> storedKeys,
                                                                  Set<String> liveKeys,
                                                                  Set<String> verifiedLines) {
        List<AssetReconciliationDelta> deltas = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AcceptanceView acceptance : distinct(acceptances)) {
            String line = blank(acceptance.acceptedLineUuid()) ? null : acceptance.acceptedLineUuid();
            String identity = line == null
                    ? "blank|" + acceptance.acceptedQuoteId() + "|" + acceptance.subscriptionId()
                    : lineKey(acceptance.subscriptionId(), line);
            if (!seen.add(identity)) {
                continue;
            }
            if (line == null) {
                deltas.add(delta(AssetReconciliationDeltaKind.UNVERIFIED_LINE_IDENTITY,
                        acceptance.subscriptionId(), null, null,
                        "acceptance " + acceptance.acceptedQuoteId()
                                + " has no accepted_line_uuid; line identity not invented"));
                continue;
            }
            String key = lineKey(acceptance.subscriptionId(), line);
            if (!storedKeys.contains(key)) {
                deltas.add(delta(AssetReconciliationDeltaKind.ACCEPTANCE_WITHOUT_ASSET,
                        acceptance.subscriptionId(), line, null,
                        "acceptance " + acceptance.acceptedQuoteId()
                                + " subscription " + acceptance.subscriptionId()
                                + " line " + line + " has no assets row"));
            }
            if (unverified(line, verifiedLines) && !liveKeys.contains(key)) {
                deltas.add(delta(AssetReconciliationDeltaKind.UNVERIFIED_LINE_IDENTITY,
                        acceptance.subscriptionId(), line, null, unverifiedDetail(line, verifiedLines)));
            }
        }
        return deltas;
    }

    private List<AssetReconciliationDelta> assetDeltas(List<AssetView> assets,
                                                       Set<String> createdFor,
                                                       Set<String> verifiedLines,
                                                       Map<String, InstalledAssetDto> projected,
                                                       LocalDate asOf) {
        List<AssetReconciliationDelta> deltas = new ArrayList<>();
        for (AssetView asset : assets) {
            if (asset.deleted()) {
                continue;
            }
            if (!createdFor.contains(asset.uuid())) {
                deltas.add(delta(AssetReconciliationDeltaKind.ASSET_WITHOUT_CREATED_EVENT,
                        asset.subscriptionId(), asset.contractLineId(), asset.uuid(),
                        "asset " + asset.uuid() + " has no AssetCreated outbox row"));
            }
            if (unverified(asset.contractLineId(), verifiedLines)) {
                deltas.add(delta(AssetReconciliationDeltaKind.UNVERIFIED_LINE_IDENTITY,
                        asset.subscriptionId(), blank(asset.contractLineId()) ? null : asset.contractLineId(),
                        asset.uuid(), unverifiedDetail(asset.contractLineId(), verifiedLines)));
            }
            String mismatch = mismatchDetail(asset, projected.get(asset.uuid()), asOf);
            if (mismatch != null) {
                deltas.add(delta(AssetReconciliationDeltaKind.STATE_MISMATCH,
                        asset.subscriptionId(), asset.contractLineId(), asset.uuid(), mismatch));
            }
        }
        return deltas;
    }

    private static List<AssetReconciliationDelta> eventDeltas(List<EventView> lifecycle, Set<String> knownAssets) {
        List<AssetReconciliationDelta> deltas = new ArrayList<>();
        for (EventView event : lifecycle) {
            if (blank(event.aggregateId())) {
                deltas.add(delta(AssetReconciliationDeltaKind.EVENT_WITHOUT_ASSET,
                        null, null, null,
                        "outbox " + event.uuid() + " " + displayType(event) + " has no asset id"));
                continue;
            }
            if (!knownAssets.contains(event.aggregateId())) {
                deltas.add(delta(AssetReconciliationDeltaKind.EVENT_WITHOUT_ASSET,
                        null, null, event.aggregateId(),
                        "outbox " + event.uuid() + " " + displayType(event)
                                + " references asset " + event.aggregateId() + " with no assets row"));
            }
        }
        return deltas;
    }

    private static List<AssetReconciliationDelta> projectionOnlyDeltas(Map<String, InstalledAssetDto> projected,
                                                                       List<AssetView> assets,
                                                                       LocalDate asOf) {
        Set<String> live = new HashSet<>();
        for (AssetView asset : assets) {
            if (!asset.deleted() && !blank(asset.uuid())) {
                live.add(asset.uuid());
            }
        }
        List<AssetReconciliationDelta> deltas = new ArrayList<>();
        for (InstalledAssetDto installed : projected.values()) {
            if (live.contains(installed.getAssetId())) {
                continue;
            }
            deltas.add(delta(AssetReconciliationDeltaKind.STATE_MISMATCH,
                    installed.getSubscriptionId(), installed.getContractLineId(), installed.getAssetId(),
                    "installed base as of " + asOf + " includes asset " + installed.getAssetId()
                            + " with no live assets row"));
        }
        return deltas;
    }

    private static String mismatchDetail(AssetView asset, InstalledAssetDto projected, LocalDate asOf) {
        AssetStatus status = parseStatus(asset.status());
        if (projected == null) {
            if (status == AssetStatus.TERMINATED || status == AssetStatus.SUPERSEDED) {
                return null;
            }
            return "asset " + asset.uuid() + " is " + asset.status()
                    + " but is absent from the installed base as of " + asOf;
        }
        List<String> diffs = new ArrayList<>();
        if (!Objects.equals(asset.status(), projected.getStatus() == null ? null : projected.getStatus().name())) {
            diffs.add("status assets=" + asset.status() + " projection=" + projected.getStatus());
        }
        if (!Objects.equals(asset.contractLineId(), projected.getContractLineId())) {
            diffs.add("contractLineId assets=" + asset.contractLineId()
                    + " projection=" + projected.getContractLineId());
        }
        if (!Objects.equals(asset.subscriptionId(), projected.getSubscriptionId())) {
            diffs.add("subscriptionId assets=" + asset.subscriptionId()
                    + " projection=" + projected.getSubscriptionId());
        }
        if (!sameQuantity(asset.quantity(), projected.getQuantity())) {
            diffs.add("quantity assets=" + asset.quantity() + " projection=" + projected.getQuantity());
        }
        if (!Objects.equals(asset.productRef(), projected.getProductRef())) {
            diffs.add("productRef assets=" + asset.productRef() + " projection=" + projected.getProductRef());
        }
        if (!Objects.equals(asset.effectiveStart(), projected.getEffectiveStart())) {
            diffs.add("effectiveStart assets=" + asset.effectiveStart()
                    + " projection=" + projected.getEffectiveStart());
        }
        if (!Objects.equals(asset.effectiveEnd(), projected.getEffectiveEnd())) {
            diffs.add("effectiveEnd assets=" + asset.effectiveEnd() + " projection=" + projected.getEffectiveEnd());
        }
        if (diffs.isEmpty()) {
            return null;
        }
        return "asset " + asset.uuid() + " " + String.join(", ", diffs);
    }

    private static boolean unverified(String line, Set<String> verifiedLines) {
        return blank(line) || line.endsWith(SUCCESSOR_SUFFIX) || !verifiedLines.contains(line);
    }

    private static String unverifiedDetail(String line, Set<String> verifiedLines) {
        if (blank(line)) {
            return "line identity is blank; not invented";
        }
        boolean successor = line.endsWith(SUCCESSOR_SUFFIX);
        boolean unmatched = !verifiedLines.contains(line);
        if (successor && unmatched) {
            return "line " + line + " ends with :successor and does not match contract_lines.accepted_line_uuid";
        }
        if (successor) {
            return "line " + line + " ends with :successor";
        }
        return "line " + line + " does not match contract_lines.accepted_line_uuid";
    }

    private static List<AcceptanceView> distinct(List<AcceptanceView> acceptances) {
        Set<AcceptanceView> distinct = new LinkedHashSet<>();
        for (AcceptanceView acceptance : acceptances) {
            distinct.add(new AcceptanceView(
                    acceptance.acceptedQuoteId(),
                    acceptance.subscriptionId(),
                    blank(acceptance.acceptedLineUuid()) ? null : acceptance.acceptedLineUuid()));
        }
        return List.copyOf(distinct);
    }

    private static boolean isLifecycle(EventView event) {
        return matches(event, EventContractRegistry.ASSET_CREATED_V1.eventType(), KafkaTopics.ASSET_CREATED)
                || matches(event, EventContractRegistry.ASSET_AMENDED_V1.eventType(), KafkaTopics.ASSET_AMENDED)
                || matches(event, EventContractRegistry.ASSET_TERMINATED_V1.eventType(), KafkaTopics.ASSET_TERMINATED)
                || matches(event, EventContractRegistry.ASSET_SUPERSEDED_V1.eventType(), KafkaTopics.ASSET_SUPERSEDED);
    }

    private static boolean isCreated(EventView event) {
        return matches(event, EventContractRegistry.ASSET_CREATED_V1.eventType(), KafkaTopics.ASSET_CREATED);
    }

    private static boolean matches(EventView event, String eventType, String topic) {
        return eventType.equals(event.eventType()) || topic.equals(event.eventType()) || topic.equals(event.topic());
    }

    private static String displayType(EventView event) {
        if (isCreated(event)) {
            return EventContractRegistry.ASSET_CREATED_V1.eventType();
        }
        if (matches(event, EventContractRegistry.ASSET_AMENDED_V1.eventType(), KafkaTopics.ASSET_AMENDED)) {
            return EventContractRegistry.ASSET_AMENDED_V1.eventType();
        }
        if (matches(event, EventContractRegistry.ASSET_TERMINATED_V1.eventType(), KafkaTopics.ASSET_TERMINATED)) {
            return EventContractRegistry.ASSET_TERMINATED_V1.eventType();
        }
        if (matches(event, EventContractRegistry.ASSET_SUPERSEDED_V1.eventType(), KafkaTopics.ASSET_SUPERSEDED)) {
            return EventContractRegistry.ASSET_SUPERSEDED_V1.eventType();
        }
        return event.eventType();
    }

    private static AssetStatus parseStatus(String status) {
        if (blank(status)) {
            return null;
        }
        return AssetStatus.valueOf(status);
    }

    private static boolean sameQuantity(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private static long count(Map<String, Object> counts, String key) {
        Object value = counts.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("missing count " + key);
    }

    private static AssetReconciliationDelta delta(AssetReconciliationDeltaKind kind, Long subscriptionId,
                                                  String contractLineId, String assetUuid, String detail) {
        return AssetReconciliationDelta.builder()
                .kind(kind)
                .subscriptionId(subscriptionId)
                .contractLineId(contractLineId)
                .assetUuid(assetUuid)
                .detail(detail)
                .build();
    }

    private static String lineKey(Long subscriptionId, String line) {
        return subscriptionId + "|" + (line == null ? "" : line);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private AcceptanceView mapAcceptance(ResultSet rs, int rowNum) throws SQLException {
        return new AcceptanceView(
                rs.getString("accepted_quote_id"),
                rs.getObject("subscription_id", Long.class),
                rs.getString("accepted_line_uuid"));
    }

    private AssetView mapAsset(ResultSet rs, int rowNum) throws SQLException {
        return new AssetView(
                rs.getString("uuid"),
                rs.getString("account_id"),
                rs.getString("contract_line_id"),
                rs.getObject("subscription_id", Long.class),
                rs.getString("product_ref"),
                rs.getBigDecimal("quantity"),
                rs.getString("status"),
                rs.getObject("effective_start", LocalDate.class),
                rs.getObject("effective_end", LocalDate.class),
                rs.getBoolean("is_deleted"));
    }

    private EventView mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new EventView(
                rs.getString("uuid"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("topic"));
    }

    private static final Comparator<AssetReconciliationDelta> DELTA_ORDER = Comparator
            .comparing((AssetReconciliationDelta delta) -> delta.getKind().name())
            .thenComparing(delta -> delta.getSubscriptionId() == null ? Long.MAX_VALUE : delta.getSubscriptionId())
            .thenComparing(delta -> delta.getContractLineId() == null ? "" : delta.getContractLineId())
            .thenComparing(delta -> delta.getAssetUuid() == null ? "" : delta.getAssetUuid())
            .thenComparing(delta -> delta.getDetail() == null ? "" : delta.getDetail());

    record AcceptanceView(String acceptedQuoteId, Long subscriptionId, String acceptedLineUuid) {
    }

    record AssetView(String uuid, String accountId, String contractLineId, Long subscriptionId,
                     String productRef, BigDecimal quantity, String status, LocalDate effectiveStart,
                     LocalDate effectiveEnd, boolean deleted) {
    }

    record EventView(String uuid, String aggregateId, String eventType, String topic) {
    }
}
