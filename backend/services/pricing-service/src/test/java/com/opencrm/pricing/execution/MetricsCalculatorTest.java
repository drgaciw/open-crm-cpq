package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Metrics Calculator")
class MetricsCalculatorTest {

    private final MetricsCalculator metricsCalculator = new MetricsCalculator();

    @Test
    @DisplayName("calculates ARR, TCV, and ACV from recurring monthly line totals")
    void calculatesDerivedQuoteMetrics() {
        PricingResult result = PricingResult.builder()
                .lineResults(List.of(
                        line("100.00", "10.00", "0.00"),
                        line("50.00", "0.00", "5.00")
                ))
                .build();

        FinancialMetrics metrics = metricsCalculator.calculate(result, 24);

        assertEquals(new BigDecimal("1800.00"), metrics.getArr());
        assertEquals(new BigDecimal("3600.00"), metrics.getTcv());
        assertEquals(new BigDecimal("1800.00"), metrics.getAcv());
        assertEquals(new BigDecimal("7200.00"), metrics.getClv());
        assertEquals(new BigDecimal("10.00"), metrics.getTotalDiscount());
        assertEquals(new BigDecimal("5.00"), metrics.getTotalSurcharge());
    }

    @Test
    @DisplayName("annualizes short terms instead of treating ACV as partial-term TCV")
    void annualizesShortTermAcv() {
        PricingResult result = PricingResult.builder()
                .lineResults(List.of(line("200.00", "0.00", "0.00")))
                .build();

        FinancialMetrics metrics = metricsCalculator.calculate(result, 6);

        assertEquals(new BigDecimal("2400.00"), metrics.getArr());
        assertEquals(new BigDecimal("1200.00"), metrics.getTcv());
        assertEquals(new BigDecimal("2400.00"), metrics.getAcv());
    }

    @Test
    @DisplayName("defaults invalid durations to twelve months")
    void defaultsInvalidDurations() {
        PricingResult result = PricingResult.builder()
                .lineResults(List.of(line("125.00", "0.00", "0.00")))
                .build();

        FinancialMetrics metrics = metricsCalculator.calculate(result, null);

        assertEquals(new BigDecimal("1500.00"), metrics.getArr());
        assertEquals(new BigDecimal("1500.00"), metrics.getTcv());
        assertEquals(new BigDecimal("1500.00"), metrics.getAcv());
    }

    @Test
    @DisplayName("returns zero metrics for empty results")
    void returnsZeroMetricsForEmptyResults() {
        FinancialMetrics metrics = metricsCalculator.calculate(PricingResult.builder().build(), 12);

        assertEquals(BigDecimal.ZERO, metrics.getArr());
        assertEquals(BigDecimal.ZERO, metrics.getTcv());
        assertEquals(BigDecimal.ZERO, metrics.getAcv());
    }

    private PricingLineResult line(String finalPrice, String discountAmount, String surchargeAmount) {
        return PricingLineResult.builder()
                .finalPrice(new BigDecimal(finalPrice))
                .discountAmount(new BigDecimal(discountAmount))
                .surchargeAmount(new BigDecimal(surchargeAmount))
                .build();
    }
}
