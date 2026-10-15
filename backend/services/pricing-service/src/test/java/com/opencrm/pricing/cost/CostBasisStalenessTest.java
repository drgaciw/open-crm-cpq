package com.opencrm.pricing.cost;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@DisplayName("F-2 staleness: a cost is usable for two quarters (six calendar months) from its effective date")
class CostBasisStalenessTest {

    private static final LocalDate V1 = LocalDate.of(2026, 10, 1);

    @Test
    @DisplayName("mock v1 (effective 2026-10-01) is usable on 2027-03-31 and stale on 2027-04-01")
    void boundaryOfMockVersionOne() {
        assertThat(CostBasisStaleness.staleFrom(V1)).isEqualTo(LocalDate.of(2027, 4, 1));
        assertThat(CostBasisStaleness.isStale(V1, LocalDate.of(2027, 3, 31))).isFalse();
        assertThat(CostBasisStaleness.isStale(V1, LocalDate.of(2027, 4, 1))).isTrue();
    }

    @Test
    @DisplayName("on the effective date itself the cost is usable")
    void effectiveDateIsUsable() {
        assertThat(CostBasisStaleness.isStale(V1, V1)).isFalse();
    }

    @Test
    @DisplayName("a month-end effective date clamps to the last day of the sixth month")
    void monthEndClamps() {
        LocalDate endOfAugust = LocalDate.of(2026, 8, 31);
        assertThat(CostBasisStaleness.staleFrom(endOfAugust)).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(CostBasisStaleness.isStale(endOfAugust, LocalDate.of(2027, 2, 27))).isFalse();
        assertThat(CostBasisStaleness.isStale(endOfAugust, LocalDate.of(2027, 2, 28))).isTrue();
    }
}
