package com.opencrm.pricing.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("JpaCostBasisLookup: the cost effective on a date, failing closed")
class JpaCostBasisLookupTest {

    private static final LocalDate Q4 = LocalDate.of(2026, 10, 1);
    private static final LocalDate Q1 = LocalDate.of(2027, 1, 1);

    private final List<CostBasisRow> stored = new ArrayList<>();
    private JpaCostBasisLookup lookup;

    @BeforeEach
    void inMemoryRepository() {
        CostBasisRowRepository repository = mock(CostBasisRowRepository.class);
        when(repository.findFirstByProductExternalIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(
                anyString(), any(LocalDate.class))).thenAnswer(call -> stored.stream()
                .filter(row -> row.getProductExternalId().equals(call.getArgument(0)))
                .filter(row -> !row.getEffectiveFrom().isAfter(call.getArgument(1)))
                .max(Comparator.comparing(CostBasisRow::getEffectiveFrom)));
        when(repository.findByProductExternalIdAndEffectiveFrom(anyString(), any(LocalDate.class)))
                .thenAnswer(call -> stored.stream()
                        .filter(row -> row.getProductExternalId().equals(call.getArgument(0)))
                        .filter(row -> row.getEffectiveFrom().equals(call.getArgument(1)))
                        .toList());
        lookup = new JpaCostBasisLookup(repository);
    }

    @Test
    @DisplayName("PROD-SEAT v1 sums DIRECT 6.08 and COST_TO_SERVE 4.40 to 10.48 per seat per month")
    void sumsComponents() {
        store("PROD-SEAT", CostComponent.DIRECT, "6.0800", Q4, 1);
        store("PROD-SEAT", CostComponent.COST_TO_SERVE, "4.4000", Q4, 1);

        UnitCost cost = lookup.find("PROD-SEAT", LocalDate.of(2026, 11, 15)).orElseThrow();

        assertThat(cost.unitCost()).isEqualByComparingTo("10.48");
        assertThat(cost.unitCost().scale()).isEqualTo(4);
        assertThat(cost.billingUnit()).isEqualTo("seat");
        assertThat(cost.currency()).isEqualTo("USD");
        assertThat(cost.effectiveFrom()).isEqualTo(Q4);
        assertThat(cost.version()).isEqualTo(1L);
        assertThat(cost.components()).containsOnly(
                Map.entry(CostComponent.DIRECT, new BigDecimal("6.0800")),
                Map.entry(CostComponent.COST_TO_SERVE, new BigDecimal("4.4000")));
    }

    @Test
    @DisplayName("no row, a blank id or a null date gives empty")
    void emptyWhenNothing() {
        assertThat(lookup.find("UNKNOWN", Q4)).isEmpty();
        assertThat(lookup.find(" ", Q4)).isEmpty();
        assertThat(lookup.find(null, Q4)).isEmpty();
        assertThat(lookup.find("PROD-SEAT", null)).isEmpty();
    }

    @Test
    @DisplayName("a row effective after asOf is not used; the day before its effective date is empty")
    void futureRowsAreIgnored() {
        store("P", CostComponent.DIRECT, "1.0000", Q4, 1);
        assertThat(lookup.find("P", Q4.minusDays(1))).isEmpty();
        assertThat(lookup.find("P", Q4)).isPresent();
    }

    @Test
    @DisplayName("the newest effective date governs; an older date's components are not mixed in")
    void newestDateGoverns() {
        store("P", CostComponent.DIRECT, "1.0000", Q4, 1);
        store("P", CostComponent.COST_TO_SERVE, "2.0000", Q4, 1);
        store("P", CostComponent.DIRECT, "1.5000", Q1, 1);

        assertThat(lookup.find("P", LocalDate.of(2026, 12, 31)).orElseThrow().unitCost()).isEqualByComparingTo("3");
        UnitCost q1 = lookup.find("P", LocalDate.of(2027, 2, 1)).orElseThrow();
        assertThat(q1.unitCost()).isEqualByComparingTo("1.5");
        assertThat(q1.effectiveFrom()).isEqualTo(Q1);
        assertThat(q1.components()).containsOnlyKeys(CostComponent.DIRECT);
    }

    @Test
    @DisplayName("a higher version replaces the same component on the same date; other components keep their version")
    void highestVersionPerComponent() {
        store("P", CostComponent.DIRECT, "1.0000", Q4, 1);
        store("P", CostComponent.COST_TO_SERVE, "2.0000", Q4, 1);
        store("P", CostComponent.DIRECT, "1.2500", Q4, 3);

        UnitCost cost = lookup.find("P", Q4).orElseThrow();

        assertThat(cost.unitCost()).isEqualByComparingTo("3.25");
        assertThat(cost.version()).isEqualTo(3L);
        assertThat(cost.components().get(CostComponent.DIRECT)).isEqualByComparingTo("1.25");
    }

    @Test
    @DisplayName("stale: v1 from 2026-10-01 is usable on 2027-03-31 and empty from 2027-04-01")
    void staleIsEmpty() {
        store("P", CostComponent.DIRECT, "1.0000", Q4, 1);
        assertThat(lookup.find("P", LocalDate.of(2027, 3, 31))).isPresent();
        assertThat(lookup.find("P", LocalDate.of(2027, 4, 1))).isEmpty();
    }

    @Test
    @DisplayName("mixed currencies or billing units in the effective set fail closed")
    void inconsistentSetFailsClosed() {
        store("P", CostComponent.DIRECT, "1.0000", Q4, 1);
        stored.add(row("P", CostComponent.COST_TO_SERVE, "seat", "1.0000", "EUR", Q4, 1));
        assertThat(lookup.find("P", Q4)).isEmpty();

        store("Q", CostComponent.DIRECT, "1.0000", Q4, 1);
        stored.add(row("Q", CostComponent.COST_TO_SERVE, "subscription", "1.0000", "USD", Q4, 1));
        assertThat(lookup.find("Q", Q4)).isEmpty();
    }

    @Test
    @DisplayName("deterministic: the same rows in any order give an equal UnitCost")
    void deterministic() {
        store("P", CostComponent.COST_TO_SERVE, "2.0000", Q4, 1);
        store("P", CostComponent.DIRECT, "1.0000", Q4, 2);
        store("P", CostComponent.DIRECT, "0.5000", Q4, 1);
        Optional<UnitCost> first = lookup.find("P", Q4);
        java.util.Collections.reverse(stored);
        assertThat(lookup.find("P", Q4)).isEqualTo(first);
    }

    @Test
    @DisplayName("B1: priced 2026-10-15 on fresh v1, a period starting 2028-10-15 still gets v1 at 10.48")
    void farFuturePeriodPricedOnFreshBasis() {
        storeSeatVersionOne();

        UnitCost cost = lookup.find("PROD-SEAT", LocalDate.of(2028, 10, 15), LocalDate.of(2026, 10, 15))
                .orElseThrow();

        assertThat(cost.unitCost()).isEqualByComparingTo("10.48");
        assertThat(cost.effectiveFrom()).isEqualTo(Q4);
        assertThat(cost.version()).isEqualTo(1L);
    }

    @Test
    @DisplayName("B1: priced on 2027-04-01, v1 is stale, so every period is empty, past or future")
    void stalePricingDateFailsClosed() {
        storeSeatVersionOne();
        LocalDate pricedOn = LocalDate.of(2027, 4, 1);

        assertThat(lookup.find("PROD-SEAT", LocalDate.of(2028, 10, 15), pricedOn)).isEmpty();
        assertThat(lookup.find("PROD-SEAT", pricedOn, pricedOn)).isEmpty();
        assertThat(lookup.find("PROD-SEAT", LocalDate.of(2026, 11, 1), pricedOn)).isEmpty();
        assertThat(lookup.find("PROD-SEAT", LocalDate.of(2027, 3, 31), LocalDate.of(2027, 3, 31))).isPresent();
    }

    @Test
    @DisplayName("B1: a future-dated row effective 2027-07-01 governs a period starting 2027-10-15 priced 2026-10-15")
    void futureDatedRowGovernsLaterPeriod() {
        storeSeatVersionOne();
        LocalDate q3 = LocalDate.of(2027, 7, 1);
        store("PROD-SEAT", CostComponent.DIRECT, "7.0000", q3, 2);
        store("PROD-SEAT", CostComponent.COST_TO_SERVE, "5.0000", q3, 2);

        UnitCost later = lookup.find("PROD-SEAT", LocalDate.of(2027, 10, 15), LocalDate.of(2026, 10, 15))
                .orElseThrow();
        assertThat(later.effectiveFrom()).isEqualTo(q3);
        assertThat(later.unitCost()).isEqualByComparingTo("12.00");
        assertThat(later.version()).isEqualTo(2L);

        UnitCost first = lookup.find("PROD-SEAT", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 15))
                .orElseThrow();
        assertThat(first.effectiveFrom()).isEqualTo(Q4);
        assertThat(first.unitCost()).isEqualByComparingTo("10.48");
    }

    @Test
    @DisplayName("B1: freshness is judged on the rows effective on the pricing date, never on a future period start")
    void freshnessIsJudgedOnThePricingDate() {
        storeSeatVersionOne();
        // 2028-10-15 is two years after v1: judged against the period start, it would be stale.
        assertThat(CostBasisStaleness.isStale(Q4, LocalDate.of(2028, 10, 15))).isTrue();
        assertThat(lookup.find("PROD-SEAT", LocalDate.of(2028, 10, 15), LocalDate.of(2027, 3, 31))).isPresent();
    }

    @Test
    @DisplayName("B1: a refresh effective after the pricing date does not rescue a basis stale on the pricing date")
    void laterRefreshDoesNotRescueStalePricingDate() {
        storeSeatVersionOne();
        store("PROD-SEAT", CostComponent.DIRECT, "7.0000", LocalDate.of(2027, 7, 1), 2);

        assertThat(lookup.find("PROD-SEAT", LocalDate.of(2027, 10, 15), LocalDate.of(2027, 5, 1))).isEmpty();
    }

    @Test
    @DisplayName("B1: with no row effective on the pricing date, a Finance-approved forward cost is used")
    void onlyForwardRowsAreUsable() {
        store("P", CostComponent.DIRECT, "2.0000", Q1, 1);

        assertThat(lookup.find("P", LocalDate.of(2026, 12, 1), LocalDate.of(2026, 10, 15))).isEmpty();
        assertThat(lookup.find("P", Q1, LocalDate.of(2026, 10, 15)).orElseThrow().unitCost())
                .isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("B1: a null id or either null date gives empty; the 2-arg default equals both dates the same")
    void threeArgumentNullsAndDefault() {
        storeSeatVersionOne();
        assertThat(lookup.find("PROD-SEAT", null, Q4)).isEmpty();
        assertThat(lookup.find("PROD-SEAT", Q4, null)).isEmpty();
        assertThat(lookup.find(null, Q4, Q4)).isEmpty();
        LocalDate day = LocalDate.of(2026, 11, 15);
        assertThat(lookup.find("PROD-SEAT", day)).isEqualTo(lookup.find("PROD-SEAT", day, day));
    }

    private void storeSeatVersionOne() {
        store("PROD-SEAT", CostComponent.DIRECT, "6.0800", Q4, 1);
        store("PROD-SEAT", CostComponent.COST_TO_SERVE, "4.4000", Q4, 1);
    }

    private void store(String product, CostComponent component, String cost, LocalDate from, long version) {
        stored.add(row(product, component, "seat", cost, "USD", from, version));
    }

    static CostBasisRow row(String product, CostComponent component, String unit, String cost, String currency,
                            LocalDate from, long version) {
        return CostBasisRow.builder()
                .productExternalId(product)
                .component(component)
                .billingUnit(unit)
                .unitCost(new BigDecimal(cost))
                .currency(currency)
                .effectiveFrom(from)
                .version(version)
                .approvedBy("Finance")
                .ingestedAt(Instant.parse("2026-10-15T00:00:00Z"))
                .ingestedBy("tester")
                .importId(1L)
                .build();
    }
}
