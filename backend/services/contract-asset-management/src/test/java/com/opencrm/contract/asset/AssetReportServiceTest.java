package com.opencrm.contract.asset;

import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.contract.asset.fixtures.AssetAmendedFixture;
import com.opencrm.contract.asset.fixtures.AssetSupersededFixture;
import com.opencrm.contract.asset.fixtures.AssetTerminatedFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Renewal window, churn classification, and the exclusion of supersede. */
class AssetReportServiceTest {

    private static final String ACCOUNT = "cust-100";
    private static final String OTHER = "cust-200";
    private static final String ASSET = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1";
    private static final String SECOND = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2";
    private static final String SUCCESSOR = "cccccccc-cccc-4ccc-8ccc-ccccccccccc3";

    private InMemoryAssetHistoryStore store;
    private AssetHistoryProjection projection;
    private AssetReportService reports;

    @BeforeEach
    void setUp() {
        AssetReadTestCaller.signIn();
        store = new InMemoryAssetHistoryStore();
        projection = new AssetHistoryProjection(store);
        reports = new AssetReportService(projection, store);
    }

    @AfterEach
    void clearAuthorization() {
        AssetReadTestCaller.clear();
    }

    @Test
    void renewalWindowIsInclusiveAndSkipsTerminatedSupersededAndOtherAccounts() {
        created(ASSET, ACCOUNT, "10", "2026-01-01", "2026-06-01");
        created(SECOND, ACCOUNT, "3", "2026-01-01", "2026-06-30");
        created("dddddddd-dddd-4ddd-8ddd-ddddddddddd4", ACCOUNT, "1", "2026-01-01", "2026-05-31");
        created("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeee5", ACCOUNT, "1", "2026-01-01", "2026-07-01");
        created("ffffffff-ffff-4fff-8fff-fffffffffff6", ACCOUNT, "1", "2026-01-01", "2026-06-15");
        projection.apply(AssetTerminatedFixture.of(
                "evt-t", "ffffffff-ffff-4fff-8fff-fffffffffff6", ACCOUNT,
                LocalDate.parse("2026-04-01"), "amd-t", "1"));
        created("99999999-9999-4999-8999-999999999999", ACCOUNT, "2", "2026-01-01", "2026-06-10");
        created(SUCCESSOR, ACCOUNT, "2", "2026-05-01", "2026-06-20");
        projection.apply(AssetSupersededFixture.of(
                "evt-s", "99999999-9999-4999-8999-999999999999", ACCOUNT,
                LocalDate.parse("2026-05-01"), "amd-s", "1", SUCCESSOR));
        created("88888888-8888-4888-8888-888888888888", OTHER, "9", "2026-01-01", "2026-06-15");

        UpcomingRenewalsReport report = reports.upcomingRenewals(
                ACCOUNT, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), null, null);

        assertThat(report.getAssetCount()).isEqualTo(3);
        assertThat(report.getAssets()).extracting(InstalledAssetDto::getAssetId)
                .containsExactly(ASSET, SUCCESSOR, SECOND);
    }

    @Test
    void withinDaysCountsFromAsOfOrFromTheClock() {
        created(ASSET, ACCOUNT, "1", "2026-01-01", "2026-06-11");
        created(SECOND, ACCOUNT, "1", "2026-01-01", "2026-06-12");

        UpcomingRenewalsReport fromAsOf = reports.upcomingRenewals(
                ACCOUNT, null, null, 10, LocalDate.parse("2026-06-01"));
        assertThat(fromAsOf.getWindowStart()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(fromAsOf.getWindowEnd()).isEqualTo(LocalDate.parse("2026-06-11"));
        assertThat(fromAsOf.getAssets()).extracting(InstalledAssetDto::getAssetId).containsExactly(ASSET);

        Clock clock = Clock.fixed(Instant.parse("2026-06-01T15:00:00Z"), ZoneOffset.UTC);
        AssetReportService clocked = new AssetReportService(projection, store, clock);
        UpcomingRenewalsReport fromClock = clocked.upcomingRenewals(ACCOUNT, null, null, 0, null);
        assertThat(fromClock.getWindowStart()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(fromClock.getWindowEnd()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(fromClock.getAssets()).isEmpty();
    }

    @Test
    void renewalWindowRejectsIncompleteOrContradictoryBounds() {
        assertThatThrownBy(() -> reports.upcomingRenewals(ACCOUNT, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("windowStart");
        assertThatThrownBy(() -> reports.upcomingRenewals(
                ACCOUNT, LocalDate.parse("2026-06-30"), LocalDate.parse("2026-06-01"), null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reports.upcomingRenewals(
                ACCOUNT, LocalDate.parse("2026-06-01"), LocalDate.parse("2026-06-30"), 5, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reports.upcomingRenewals(ACCOUNT, null, null, -1, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> reports.upcomingRenewals("  ", null, null, 1, LocalDate.parse("2026-06-01")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void terminationOnThePeriodBoundsIsChurnAndAnUpsizeIsNot() {
        created(ASSET, ACCOUNT, "4", "2026-01-01", "2026-12-31");
        created(SECOND, ACCOUNT, "6", "2026-01-01", "2026-12-31");
        projection.apply(AssetTerminatedFixture.of(
                "evt-start", ASSET, ACCOUNT, LocalDate.parse("2026-04-01"), "amd-1", "1"));
        projection.apply(AssetAmendedFixture.of(
                "evt-up", SECOND, ACCOUNT, LocalDate.parse("2026-06-30"), "amd-2", "1", new BigDecimal("9")));

        AssetChurnReport report = reports.churn(
                ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"));

        assertThat(report.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getChurnKind()).isEqualTo(AssetChurnKind.TERMINATED);
            assertThat(event.getAssetId()).isEqualTo(ASSET);
            assertThat(event.getQuantityBefore()).isEqualByComparingTo("4");
            assertThat(event.getQuantityAfter()).isEqualByComparingTo("0");
            assertThat(event.getQuantityChurned()).isEqualByComparingTo("4");
        });
        assertThat(report.getQuantityChurned()).isEqualByComparingTo("4");
        assertThat(report.getAssetCount()).isEqualTo(1);
    }

    @Test
    void terminationOutsideThePeriodIsNotChurn() {
        created(ASSET, ACCOUNT, "4", "2026-01-01", "2026-12-31");
        projection.apply(AssetTerminatedFixture.of(
                "evt-before", ASSET, ACCOUNT, LocalDate.parse("2026-03-31"), "amd-1", "1"));
        created(SECOND, ACCOUNT, "2", "2026-01-01", "2026-12-31");
        projection.apply(AssetTerminatedFixture.of(
                "evt-after", SECOND, ACCOUNT, LocalDate.parse("2026-07-01"), "amd-2", "1"));

        AssetChurnReport report = reports.churn(
                ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"));

        assertThat(report.getEvents()).isEmpty();
        assertThat(report.getQuantityChurned()).isEqualByComparingTo("0");
    }

    @Test
    void supersedeIsNotChurnEvenWhenTheSuccessorQuantityIsLower() {
        created(ASSET, ACCOUNT, "10", "2026-01-01", "2026-12-31");
        created(SUCCESSOR, ACCOUNT, "3", "2026-05-01", "2026-12-31");
        projection.apply(AssetSupersededFixture.of(
                "evt-s", ASSET, ACCOUNT, LocalDate.parse("2026-05-01"), "amd-s", "1", SUCCESSOR));
        projection.apply(AssetTerminatedFixture.of(
                "evt-t", ASSET, ACCOUNT, LocalDate.parse("2026-05-01"), "amd-t", "1"));

        AssetChurnReport report = reports.churn(
                ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"));

        assertThat(report.getEvents()).isEmpty();
        assertThat(report.getAssetCount()).isZero();
    }

    @Test
    void downsizeBeforeALaterSupersedeIsChurnAndTheSupersedeIsNot() {
        created(ASSET, ACCOUNT, "10", "2026-01-01", "2026-12-31");
        projection.apply(AssetAmendedFixture.of(
                "evt-down", ASSET, ACCOUNT, LocalDate.parse("2026-05-01"), "amd-d", "1", new BigDecimal("7")));
        projection.apply(AssetSupersededFixture.of(
                "evt-s", ASSET, ACCOUNT, LocalDate.parse("2026-05-20"), "amd-s", "1", SUCCESSOR));
        created(SUCCESSOR, ACCOUNT, "7", "2026-05-20", "2026-12-31");

        AssetChurnReport report = reports.churn(
                ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"));

        assertThat(report.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getChurnKind()).isEqualTo(AssetChurnKind.DOWNSIZED);
            assertThat(event.getQuantityBefore()).isEqualByComparingTo("10");
            assertThat(event.getQuantityAfter()).isEqualByComparingTo("7");
            assertThat(event.getQuantityChurned()).isEqualByComparingTo("3");
        });
    }

    @Test
    void sameDayDownsizeStillCountsWhenTheAssetIsAlsoSuperseded() {
        created(ASSET, ACCOUNT, "10", "2026-01-01", "2026-12-31");
        projection.apply(AssetAmendedFixture.of(
                "evt-down", ASSET, ACCOUNT, LocalDate.parse("2026-04-01"), "amd-d", "1", new BigDecimal("4")));
        projection.apply(AssetSupersededFixture.of(
                "evt-s", ASSET, ACCOUNT, LocalDate.parse("2026-04-01"), "amd-s", "1", SUCCESSOR));

        AssetChurnReport report = reports.churn(
                ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"));

        assertThat(report.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getChurnKind()).isEqualTo(AssetChurnKind.DOWNSIZED);
            assertThat(event.getQuantityChurned()).isEqualByComparingTo("6");
        });
    }

    @Test
    void amendmentAfterSupersedeIsNotADownsize() {
        created(ASSET, ACCOUNT, "10", "2026-01-01", "2026-12-31");
        projection.apply(AssetSupersededFixture.of(
                "evt-s", ASSET, ACCOUNT, LocalDate.parse("2026-04-01"), "amd-s", "1", SUCCESSOR));
        projection.apply(AssetAmendedFixture.of(
                "evt-late", ASSET, ACCOUNT, LocalDate.parse("2026-04-15"), "amd-l", "1", new BigDecimal("1")));

        assertThat(reports.churn(ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30")).getEvents())
                .isEmpty();
    }

    @Test
    void downsizeUsesTheLastKnownQuantityWhenAnAmendmentOmitsQuantity() {
        created(ASSET, ACCOUNT, "10.00", "2026-01-01", "2026-12-31");
        projection.apply(new AssetHistoryFact(
                "evt-note", AssetHistoryKind.AMENDED, ASSET, ACCOUNT, LocalDate.parse("2026-04-10"),
                null, "amd-n", "1", null, null, null, null, "prod-a", null));
        projection.apply(AssetAmendedFixture.of(
                "evt-down", ASSET, ACCOUNT, LocalDate.parse("2026-05-15"), "amd-d", "1", new BigDecimal("4")));
        projection.apply(AssetAmendedFixture.of(
                "evt-same", ASSET, ACCOUNT, LocalDate.parse("2026-05-20"), "amd-e", "1", new BigDecimal("4.0")));

        AssetChurnReport report = reports.churn(
                ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"));

        assertThat(report.getEvents()).singleElement().satisfies(event -> {
            assertThat(event.getQuantityBefore()).isEqualByComparingTo("10.00");
            assertThat(event.getQuantityChurned()).isEqualByComparingTo("6.00");
            assertThat(event.getProductRef()).isEqualTo("prod-a");
        });
    }

    @Test
    void downsizeThenTerminationBothCountAndSumToTheOriginalQuantity() {
        created(ASSET, ACCOUNT, "10", "2026-01-01", "2026-12-31");
        projection.apply(AssetAmendedFixture.of(
                "evt-down", ASSET, ACCOUNT, LocalDate.parse("2026-05-01"), "amd-d", "1", new BigDecimal("6")));
        projection.apply(AssetTerminatedFixture.of(
                "evt-t", ASSET, ACCOUNT, LocalDate.parse("2026-06-01"), "amd-t", "1"));

        AssetChurnReport report = reports.churn(
                ACCOUNT, LocalDate.parse("2026-04-01"), LocalDate.parse("2026-06-30"));

        assertThat(report.getAssetCount()).isEqualTo(1);
        assertThat(report.getEventCount()).isEqualTo(2);
        assertThat(report.getEvents()).extracting(AssetChurnEventDto::getChurnKind)
                .containsExactly(AssetChurnKind.DOWNSIZED, AssetChurnKind.TERMINATED);
        assertThat(report.getQuantityChurned()).isEqualByComparingTo("10");
        assertThat(report.getEvents().get(1).getQuantityChurned()).isEqualByComparingTo("6");
    }

    @Test
    void reversedChurnPeriodIsRejected() {
        assertThatThrownBy(() -> reports.churn(
                ACCOUNT, LocalDate.parse("2026-06-30"), LocalDate.parse("2026-04-01")))
                .isInstanceOf(BusinessException.class);
    }

    private void created(String assetId, String accountId, String quantity, String start, String end) {
        projection.apply(new AssetCreatedEvent(
                assetId, accountId, "line-" + assetId.substring(0, 4), 1L, null, "prod-" + assetId.substring(0, 1),
                new BigDecimal(quantity), "ACTIVE", LocalDate.parse(start), LocalDate.parse(end)), "evt-" + assetId);
    }
}
