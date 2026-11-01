package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.contract.asset.fixtures.AssetAmendedFixture;
import com.opencrm.contract.asset.fixtures.AssetSupersededFixture;
import com.opencrm.contract.asset.fixtures.AssetTerminatedFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Report figures for one sample account match the asset Open Host Service
 * as-of query ({@code GET /api/v1/assets?accountId&asOf}).
 */
class AssetReportReconciliationTest {

    private static final String ACCOUNT = "cust-ohs";
    private static final String OTHER = "cust-other";
    private static final String ACTIVE = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String TERMINATED = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2";
    private static final String DOWNSIZED = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3";
    private static final String SUPERSEDED = "dddddddd-dddd-4ddd-8ddd-ddddddddddd4";
    private static final String SUCCESSOR = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5";
    private static final String UPSIZED = "gggggggg-gggg-4ggg-8ggg-ggggggggggg7";
    private static final String END_MOVED = "hhhhhhhh-hhhh-4hhh-8hhh-hhhhhhhhhhh8";
    private static final String OUTSIDER = "ffffffff-ffff-4fff-8fff-fffffffffff6";
    private static final LocalDate PERIOD_START = LocalDate.parse("2026-04-01");
    private static final LocalDate PERIOD_END = LocalDate.parse("2026-06-30");
    private static final LocalDate WINDOW_START = LocalDate.parse("2026-06-01");

    private AssetHistoryProjection projection;
    private AssetQueryController ohs;
    private AssetReportService reports;

    @BeforeEach
    void setUp() {
        AssetReadTestCaller.signIn();
        InMemoryAssetHistoryStore store = new InMemoryAssetHistoryStore();
        projection = new AssetHistoryProjection(store);
        ohs = new AssetQueryController(projection);
        reports = new AssetReportService(projection, store);
        sample();
    }

    @AfterEach
    void clearAuthorization() {
        AssetReadTestCaller.clear();
    }

    @Test
    void reportFiguresMatchTheInstalledBaseQueryForASampleAccount() {
        List<InstalledAssetDto> before = ohs.installedBase(ACCOUNT, PERIOD_START.minusDays(1)).getData();
        List<InstalledAssetDto> active = ohs.installedBase(ACCOUNT, PERIOD_END).getData();
        UpcomingRenewalsReport renewals = reports.upcomingRenewals(
                ACCOUNT, WINDOW_START, PERIOD_END, null, null);
        AssetChurnReport churn = reports.churn(ACCOUNT, PERIOD_START, PERIOD_END);

        assertThat(active).extracting(InstalledAssetDto::getAssetId)
                .containsExactly(ACTIVE, DOWNSIZED, SUCCESSOR, UPSIZED, END_MOVED);
        assertThat(before).extracting(InstalledAssetDto::getAssetId)
                .containsExactly(ACTIVE, TERMINATED, DOWNSIZED, SUPERSEDED, UPSIZED, END_MOVED);

        List<InstalledAssetDto> expectedRenewals = active.stream()
                .filter(asset -> asset.getEffectiveEnd() != null
                        && !asset.getEffectiveEnd().isBefore(WINDOW_START)
                        && !asset.getEffectiveEnd().isAfter(PERIOD_END))
                .sorted(java.util.Comparator.comparing(InstalledAssetDto::getEffectiveEnd)
                        .thenComparing(InstalledAssetDto::getAssetId))
                .toList();
        assertThat(renewals.getAssetCount()).isEqualTo(expectedRenewals.size());
        assertThat(renewals.getAssets()).containsExactlyElementsOf(expectedRenewals);
        assertThat(renewals.getAssets()).extracting(InstalledAssetDto::getAssetId)
                .containsExactly(ACTIVE, SUCCESSOR, END_MOVED);
        assertThat(one(renewals.getAssets(), END_MOVED).getEffectiveEnd()).isEqualTo(LocalDate.parse("2026-06-20"));
        assertThat(one(renewals.getAssets(), END_MOVED).getQuantity()).isEqualByComparingTo("1");
        assertThat(one(renewals.getAssets(), END_MOVED).getStatus()).isEqualTo(AssetStatus.AMENDED);

        assertThat(churn.getEvents()).extracting(AssetChurnEventDto::getAssetId, AssetChurnEventDto::getChurnKind)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(TERMINATED, AssetChurnKind.TERMINATED),
                        org.assertj.core.groups.Tuple.tuple(DOWNSIZED, AssetChurnKind.DOWNSIZED));
        assertThat(churn.getAssetCount()).isEqualTo(2);
        assertThat(churn.getQuantityChurned()).isEqualByComparingTo("7");

        InstalledAssetDto terminatedBefore = one(
                ohs.installedBase(ACCOUNT, LocalDate.parse("2026-04-30")).getData(), TERMINATED);
        assertThat(ohs.installedBase(ACCOUNT, LocalDate.parse("2026-05-01")).getData())
                .extracting(InstalledAssetDto::getAssetId).doesNotContain(TERMINATED);
        AssetChurnEventDto termination = churn.getEvents().get(0);
        assertThat(termination.getQuantityChurned()).isEqualByComparingTo(terminatedBefore.getQuantity());
        assertThat(active).extracting(InstalledAssetDto::getAssetId).doesNotContain(termination.getAssetId());

        InstalledAssetDto downsizedBefore = one(
                ohs.installedBase(ACCOUNT, LocalDate.parse("2026-05-14")).getData(), DOWNSIZED);
        InstalledAssetDto downsizedOn = one(active, DOWNSIZED);
        assertThat(one(ohs.installedBase(ACCOUNT, LocalDate.parse("2026-05-15")).getData(), DOWNSIZED).getQuantity())
                .isEqualByComparingTo(downsizedOn.getQuantity());
        AssetChurnEventDto downsize = churn.getEvents().get(1);
        assertThat(downsize.getQuantityBefore()).isEqualByComparingTo(downsizedBefore.getQuantity());
        assertThat(downsize.getQuantityAfter()).isEqualByComparingTo(downsizedOn.getQuantity());
        assertThat(downsize.getQuantityChurned()).isEqualByComparingTo(
                downsizedBefore.getQuantity().subtract(downsizedOn.getQuantity()));

        assertThat(churn.getEvents()).extracting(AssetChurnEventDto::getAssetId).doesNotContain(SUPERSEDED);
        assertThat(before).extracting(InstalledAssetDto::getAssetId).contains(SUPERSEDED);
        assertThat(active).extracting(InstalledAssetDto::getAssetId).doesNotContain(SUPERSEDED);
        assertThat(ohs.history(SUPERSEDED).getData()).extracting(AssetHistoryEntryDto::getEventType)
                .contains("SUPERSEDED");
        assertThat(ohs.history(SUPERSEDED).getData()).extracting(AssetHistoryEntryDto::getAssetId)
                .doesNotContain(OUTSIDER);

        assertThat(active).extracting(InstalledAssetDto::getAssetId).doesNotContain(OUTSIDER);
        assertThat(churn.getEvents()).extracting(AssetChurnEventDto::getAssetId).doesNotContain(OUTSIDER);
        assertThat(renewals.getAssets()).extracting(InstalledAssetDto::getAssetId).doesNotContain(OUTSIDER);

        assertQuantityIdentity(before, active, churn);
    }

    private void assertQuantityIdentity(List<InstalledAssetDto> before, List<InstalledAssetDto> after,
                                        AssetChurnReport churn) {
        BigDecimal start = sum(before);
        BigDecimal end = sum(after);
        BigDecimal terminated = churn.getEvents().stream()
                .filter(event -> event.getChurnKind() == AssetChurnKind.TERMINATED)
                .map(AssetChurnEventDto::getQuantityChurned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal downsized = churn.getEvents().stream()
                .filter(event -> event.getChurnKind() == AssetChurnKind.DOWNSIZED)
                .map(AssetChurnEventDto::getQuantityChurned)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<String> afterIds = after.stream().map(InstalledAssetDto::getAssetId).collect(Collectors.toSet());
        BigDecimal superseded = before.stream()
                .filter(asset -> !afterIds.contains(asset.getAssetId()))
                .filter(asset -> ohs.history(asset.getAssetId()).getData().stream()
                        .anyMatch(entry -> "SUPERSEDED".equals(entry.getEventType())
                                && !entry.getEffectiveDate().isBefore(PERIOD_START)
                                && !entry.getEffectiveDate().isAfter(PERIOD_END)))
                .map(InstalledAssetDto::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<String> beforeIds = before.stream().map(InstalledAssetDto::getAssetId).collect(Collectors.toSet());
        BigDecimal arrived = after.stream()
                .filter(asset -> !beforeIds.contains(asset.getAssetId()))
                .map(InstalledAssetDto::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal upsized = BigDecimal.ZERO;
        for (InstalledAssetDto earlier : before) {
            var later = after.stream().filter(asset -> asset.getAssetId().equals(earlier.getAssetId())).findFirst();
            if (later.isPresent()) {
                BigDecimal delta = later.get().getQuantity().subtract(earlier.getQuantity());
                if (delta.signum() > 0) {
                    upsized = upsized.add(delta);
                }
            }
        }
        assertThat(start.subtract(end)).isEqualByComparingTo(
                terminated.add(downsized).add(superseded).subtract(arrived).subtract(upsized));
        assertThat(start.subtract(end)).isEqualByComparingTo("4");
        assertThat(superseded).isEqualByComparingTo("2");
        assertThat(arrived).isEqualByComparingTo("2");
        assertThat(upsized).isEqualByComparingTo("3");
    }

    private void sample() {
        created(ACTIVE, ACCOUNT, "prod-a", "10", "2026-01-01", "2026-06-15");
        created(TERMINATED, ACCOUNT, "prod-b", "4", "2026-01-01", "2026-12-31");
        projection.apply(AssetTerminatedFixture.of(
                "evt-term", TERMINATED, ACCOUNT, LocalDate.parse("2026-05-01"), "amd-term", "1"));
        created(DOWNSIZED, ACCOUNT, "prod-c", "8", "2026-01-01", "2026-12-31");
        projection.apply(new AssetHistoryFact(
                "evt-note", AssetHistoryKind.AMENDED, DOWNSIZED, ACCOUNT, LocalDate.parse("2026-04-10"),
                null, "amd-note", "1", null, null, null, null, null, null));
        projection.apply(AssetAmendedFixture.of(
                "evt-down", DOWNSIZED, ACCOUNT, LocalDate.parse("2026-05-15"), "amd-down", "1", new BigDecimal("5")));
        created(SUPERSEDED, ACCOUNT, "prod-d", "2", "2026-01-01", "2026-08-01");
        projection.apply(AssetSupersededFixture.of(
                "evt-super", SUPERSEDED, ACCOUNT, LocalDate.parse("2026-05-20"), "amd-super", "1", SUCCESSOR));
        created(SUCCESSOR, ACCOUNT, "prod-e", "2", "2026-05-20", "2026-06-20");
        created(UPSIZED, ACCOUNT, "prod-g", "6", "2026-02-01", "2026-11-30");
        projection.apply(AssetAmendedFixture.of(
                "evt-up", UPSIZED, ACCOUNT, LocalDate.parse("2026-06-01"), "amd-up", "1", new BigDecimal("9")));
        created(END_MOVED, ACCOUNT, "prod-h", "1", "2026-01-01", "2026-12-31");
        projection.apply(new AssetHistoryFact(
                "evt-end", AssetHistoryKind.AMENDED, END_MOVED, ACCOUNT, LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-06-20"), "amd-end", "1", null, null, null, null, null, null));
        created(OUTSIDER, OTHER, "prod-f", "100", "2026-01-01", "2026-06-15");
        projection.apply(AssetTerminatedFixture.of(
                "evt-out", OUTSIDER, OTHER, LocalDate.parse("2026-05-02"), "amd-out", "1"));
    }

    private void created(String assetId, String accountId, String product, String quantity, String start, String end) {
        projection.apply(new AssetCreatedEvent(
                assetId, accountId, "line-" + assetId.substring(0, 4), 1L, null, product,
                new BigDecimal(quantity), "ACTIVE", LocalDate.parse(start), LocalDate.parse(end)), "evt-" + assetId);
    }

    private static InstalledAssetDto one(List<InstalledAssetDto> rows, String assetId) {
        return rows.stream().filter(row -> assetId.equals(row.getAssetId())).findFirst().orElseThrow();
    }

    private static BigDecimal sum(List<InstalledAssetDto> rows) {
        return rows.stream().map(InstalledAssetDto::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
