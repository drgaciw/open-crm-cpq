package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calculator for financial metrics.
 */
@Component
public class MetricsCalculator {

    /**
     * Calculate financial metrics from pricing result.
     */
    public FinancialMetrics calculate(PricingResult result, int durationInMonths) {
        if (result == null || result.getLineResults() == null || result.getLineResults().isEmpty()) {
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

        // Calculate total monthly price
        BigDecimal monthlyPrice = result.getLineResults().stream()
                .map(PricingLineResult::getFinalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ARR = monthlyPrice * 12
        BigDecimal arr = monthlyPrice.multiply(BigDecimal.valueOf(12));

        // TCV = monthlyPrice * durationInMonths
        BigDecimal tcv = monthlyPrice.multiply(BigDecimal.valueOf(durationInMonths));

        // ACV = TCV / (durationInMonths / 12.0)
        BigDecimal acv = durationInMonths >= 12
                ? tcv.divide(BigDecimal.valueOf(durationInMonths / 12.0), 2, RoundingMode.HALF_UP)
                : tcv;

        // CLV = monthlyPrice * expectedDuration (use durationInMonths * 2 as proxy)
        BigDecimal clv = monthlyPrice.multiply(BigDecimal.valueOf(durationInMonths * 2));

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
}
