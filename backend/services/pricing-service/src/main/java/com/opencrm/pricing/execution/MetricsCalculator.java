package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calculator for financial metrics.
 */
@Component
public class MetricsCalculator {

    private static final int DEFAULT_DURATION_IN_MONTHS = 12;
    private static final int MONTHS_IN_YEAR = 12;
    private static final int MONEY_SCALE = 2;

    /**
     * Calculate financial metrics from pricing result.
     */
    public FinancialMetrics calculate(PricingResult result, Integer durationInMonths) {
        if (result == null || result.getLineResults() == null || result.getLineResults().isEmpty()) {
            return zeroMetrics();
        }

        int normalizedDuration = normalizeDuration(durationInMonths);
        BigDecimal recurringMonthlyValue = result.getLineResults().stream()
                .map(PricingLineResult::getFinalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal arr = recurringMonthlyValue
                .multiply(BigDecimal.valueOf(MONTHS_IN_YEAR))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal tcv = recurringMonthlyValue
                .multiply(BigDecimal.valueOf(normalizedDuration))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal acv = tcv
                .multiply(BigDecimal.valueOf(MONTHS_IN_YEAR))
                .divide(BigDecimal.valueOf(normalizedDuration), MONEY_SCALE, RoundingMode.HALF_UP);

        // CLV = monthlyPrice * expectedDuration (use durationInMonths * 2 as proxy)
        BigDecimal clv = recurringMonthlyValue
                .multiply(BigDecimal.valueOf(normalizedDuration * 2L))
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        // Calculate total discount and surcharge
        BigDecimal totalDiscount = result.getLineResults().stream()
                .map(line -> line.getDiscountAmount() != null ? line.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSurcharge = result.getLineResults().stream()
                .map(line -> line.getSurchargeAmount() != null ? line.getSurchargeAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Estimated margin (placeholder - would need cost data)
        BigDecimal estimatedMarginPct = BigDecimal.valueOf(40.0);

        return FinancialMetrics.builder()
                .arr(arr)
                .tcv(tcv)
                .acv(acv)
                .clv(clv)
                .totalDiscount(totalDiscount)
                .totalSurcharge(totalSurcharge)
                .estimatedMarginPct(estimatedMarginPct)
                .build();
    }

    private int normalizeDuration(Integer durationInMonths) {
        return durationInMonths != null && durationInMonths > 0
                ? durationInMonths
                : DEFAULT_DURATION_IN_MONTHS;
    }

    private FinancialMetrics zeroMetrics() {
        return FinancialMetrics.builder()
                .arr(BigDecimal.ZERO)
                .tcv(BigDecimal.ZERO)
                .acv(BigDecimal.ZERO)
                .clv(BigDecimal.ZERO)
                .totalDiscount(BigDecimal.ZERO)
                .totalSurcharge(BigDecimal.ZERO)
                .estimatedMarginPct(BigDecimal.ZERO)
                .build();
    }
}
