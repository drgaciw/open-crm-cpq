package com.opencrm.pricing.execution;

import com.opencrm.pricing.execution.margin.MarginAssessment;
import com.opencrm.pricing.execution.margin.MarginSource;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** DRG-438 margin slice: F-1 margin is {@code (net − unit cost) / net}, a fraction. */
@Tag("unit")
class MetricsCalculatorMarginTest {

    private final MetricsCalculator calculator = new MetricsCalculator();

    @Test
    @DisplayName("mock v1 named seat: list 40, unit cost 10.48 is 73.8% margin")
    void mockSeatMargin() {
        assertThat(MetricsCalculator.marginFraction(new BigDecimal("40"), new BigDecimal("10.48")))
                .isEqualByComparingTo("0.7380");
    }

    @Test
    @DisplayName("the 35% floor is reached at net 16.1231 and rounding never lifts a margin over it")
    void floorBoundaryRoundsDown() {
        assertThat(MetricsCalculator.marginFraction(new BigDecimal("16.1231"), new BigDecimal("10.48")))
                .isEqualByComparingTo("0.3500");
        assertThat(MetricsCalculator.marginFraction(new BigDecimal("10.00"), new BigDecimal("6.50")))
                .isEqualByComparingTo("0.3500");
        // 0.349990... must not round up to 0.3500.
        assertThat(MetricsCalculator.marginFraction(new BigDecimal("10.00"), new BigDecimal("6.5001")))
                .isEqualByComparingTo("0.3499");
    }

    @Test
    @DisplayName("a non-positive net has no margin, and a loss is a negative fraction")
    void undefinedAndNegative() {
        assertThat(MetricsCalculator.marginFraction(BigDecimal.ZERO, new BigDecimal("1"))).isNull();
        assertThat(MetricsCalculator.marginFraction(null, new BigDecimal("1"))).isNull();
        assertThat(MetricsCalculator.marginFraction(new BigDecimal("10"), null)).isNull();
        assertThat(MetricsCalculator.marginFraction(new BigDecimal("10"), new BigDecimal("12")))
                .isEqualByComparingTo("-0.2000");
    }

    @Test
    @DisplayName("placeholder metrics keep 40.0 percent and add no margin fields")
    void placeholderUnchanged() {
        FinancialMetrics metrics = calculator.calculate(oneLine(), 12);
        assertThat(metrics.getEstimatedMarginPct()).isEqualByComparingTo("40.0");
        assertThat(metrics.getMarginFraction()).isNull();
        assertThat(metrics.getMarginSource()).isNull();
    }

    @Test
    @DisplayName("cost basis sets the fraction, the percent view of it, and the source")
    void costBasisApplied() {
        FinancialMetrics metrics = calculator.calculate(oneLine(), 12);
        calculator.applyCostBasisMargin(metrics, MarginAssessment.builder()
                .source(MarginSource.COST_BASIS.wireValue())
                .quoteMarginFraction(new BigDecimal("0.7380")).lines(List.of()).build());
        assertThat(metrics.getMarginFraction()).isEqualByComparingTo("0.7380");
        assertThat(metrics.getEstimatedMarginPct()).isEqualByComparingTo("73.80");
        assertThat(metrics.getMarginSource()).isEqualTo("cost-basis");
    }

    @Test
    @DisplayName("a missing cost gives no margin at all, never the 40.0 placeholder")
    void missingCostIsAbsent() {
        FinancialMetrics metrics = calculator.calculate(oneLine(), 12);
        calculator.applyCostBasisMargin(metrics, MarginAssessment.builder()
                .source(MarginSource.COST_BASIS.wireValue()).quoteMarginFraction(null).lines(List.of()).build());
        assertThat(metrics.getMarginFraction()).isNull();
        assertThat(metrics.getEstimatedMarginPct()).isNull();
        assertThat(metrics.getMarginSource()).isEqualTo("cost-basis");
    }

    private static PricingResult oneLine() {
        return PricingResult.builder().lineResults(List.of(PricingLineResult.builder()
                .productExternalId("PROD-SEAT").quantity(1).listPrice(new BigDecimal("40"))
                .finalPrice(new BigDecimal("40")).build())).build();
    }
}
