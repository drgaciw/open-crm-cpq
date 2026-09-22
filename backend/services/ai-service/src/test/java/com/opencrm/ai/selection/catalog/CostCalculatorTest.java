package com.opencrm.ai.selection.catalog;

import com.opencrm.ai.selection.SelectionTypes.PriceRates;
import com.opencrm.ai.selection.SelectionTypes.Usage;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class CostCalculatorTest {
    private final CostCalculator calculator = new CostCalculator();
    private final PriceRates rates = new PriceRates(new BigDecimal("0.001"), new BigDecimal("0.002"),
            new BigDecimal("0.01"), new BigDecimal("0.0001"), new BigDecimal("0.0012"));

    @Test
    void countsCacheReadWriteReasoningRequestRetriesAndEvaluatorOverheadExactly() {
        // 100 input = 50 fresh + 30 cache reads + 20 cache writes. Output already includes reasoning.
        Usage attempt = new Usage(100, 10, 30, 20, 1, null);
        assertThat(calculator.usageCost(rates, attempt)).isEqualByComparingTo("0.107");
        assertThat(calculator.costPerSuccess(rates, List.of(attempt, attempt), new BigDecimal("0.006"), 1))
                .isEqualByComparingTo("0.220");
    }

    @Test
    void preservesTrueFreePricesAndUsesGreaterProviderReportedCharges() {
        PriceRates free = new PriceRates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        assertThat(calculator.usageCost(free, new Usage(100, 100, 0, 0, 1, null))).isZero();
        assertThat(calculator.usageCost(free, new Usage(100, 100, 0, 0, 1, new BigDecimal("0.12"))))
                .isEqualByComparingTo("0.12");
    }

    @Test
    void rejectsUnknownNegativeRatesUnpricedCacheAndNoSuccessfulTasks() {
        Usage attempt = new Usage(100, 10, 30, 0, 1, null);
        assertThatThrownBy(() -> calculator.usageCost(new PriceRates(null, BigDecimal.ZERO, BigDecimal.ZERO, null, null), attempt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.usageCost(new PriceRates(BigDecimal.ONE.negate(), BigDecimal.ZERO, BigDecimal.ZERO, null, null), attempt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.usageCost(new PriceRates(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null), attempt))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.costPerSuccess(rates, List.of(attempt), BigDecimal.ZERO, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculator.batchCost(rates, List.of(attempt), BigDecimal.ONE.negate()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
