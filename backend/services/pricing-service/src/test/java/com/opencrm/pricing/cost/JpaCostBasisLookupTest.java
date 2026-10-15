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
