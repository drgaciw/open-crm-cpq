package com.opencrm.crm.fact;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactScoreCalculatorTest {

    @Test
    void oneWeightIsItself() {
        assertThat(FactScoreCalculator.score(List.of(new BigDecimal("0.95"))))
                .isEqualByComparingTo("0.9500");
    }

    @Test
    void independentWeightsCombineAsOneMinusTheProductOfComplements() {
        assertThat(FactScoreCalculator.score(List.of(new BigDecimal("0.80"), new BigDecimal("0.40"))))
                .isEqualByComparingTo("0.8800");
        assertThat(FactScoreCalculator.score(List.of(new BigDecimal("0.80"), new BigDecimal("0.80"))))
                .isEqualByComparingTo("0.9600");
    }

    @Test
    void scoreIsCappedAtNinetyNineHundredths() {
        assertThat(FactScoreCalculator.score(List.of(
                new BigDecimal("0.90"),
                new BigDecimal("0.90"),
                new BigDecimal("0.90"))))
                .isEqualByComparingTo("0.9900");
    }

    @Test
    void emptyOrOutOfRangeWeightsAreRejected() {
        assertThatThrownBy(() -> FactScoreCalculator.score(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FactScoreCalculator.score(List.of(new BigDecimal("1.01"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FactScoreCalculator.score(List.of(new BigDecimal("-0.01"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
