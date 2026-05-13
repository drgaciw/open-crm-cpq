package com.opencrm.pricing.execution;

import com.opencrm.pricing.customer.Customer;
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
        return calculate(result, durationInMonths, null);
    }

    /**
     * Calculate financial metrics from pricing result and customer context.
     */
    public FinancialMetrics calculate(PricingResult result, int durationInMonths, Customer customer) {
        if (result == null || result.getLineResults() == null || result.getLineResults().isEmpty()) {
            return FinancialMetrics.builder()
                    .arr(BigDecimal.ZERO)
                    .tcv(BigDecimal.ZERO)
                    .acv(BigDecimal.ZERO)
                    .clv(BigDecimal.ZERO)
                    .churnScore(customer != null ? customer.getChurnScore() : null)
                    .discountRate(BigDecimal.ZERO)
                    .totalDiscount(BigDecimal.ZERO)
                    .totalSurcharge(BigDecimal.ZERO)
                    .priceMultiplier(BigDecimal.ZERO)
                    .estimatedMarginPct(BigDecimal.ZERO)
                    .build();
        }

        int effectiveDurationInMonths = durationInMonths > 0 ? durationInMonths : 12;

        // Calculate total monthly price
        BigDecimal monthlyPrice = result.getLineResults().stream()
                .map(line -> line.getFinalPrice() != null ? line.getFinalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal listPrice = result.getLineResults().stream()
                .map(line -> line.getListPrice() != null
                        ? line.getListPrice().multiply(BigDecimal.valueOf(line.getQuantity() != null ? line.getQuantity() : 0))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ARR = monthlyPrice * 12
        BigDecimal arr = monthlyPrice.multiply(BigDecimal.valueOf(12));

        // TCV = monthlyPrice * durationInMonths
        BigDecimal tcv = monthlyPrice.multiply(BigDecimal.valueOf(effectiveDurationInMonths));

        // ACV = contract value normalized to the annual contract period.
        BigDecimal acv = tcv.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // CLV = monthlyPrice * expectedDuration (use durationInMonths * 2 as proxy)
        BigDecimal clv = monthlyPrice.multiply(BigDecimal.valueOf(effectiveDurationInMonths * 2L));

        // Calculate total discount and surcharge
        BigDecimal totalDiscount = result.getLineResults().stream()
                .map(line -> line.getDiscountAmount() != null ? line.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSurcharge = result.getLineResults().stream()
                .map(line -> line.getSurchargeAmount() != null ? line.getSurchargeAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountRate = listPrice.compareTo(BigDecimal.ZERO) > 0
                ? totalDiscount.divide(listPrice, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal priceMultiplier = listPrice.compareTo(BigDecimal.ZERO) > 0
                ? monthlyPrice.divide(listPrice, 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // Estimated margin (placeholder - would need cost data)
        BigDecimal estimatedMarginPct = BigDecimal.valueOf(40.0);

        return FinancialMetrics.builder()
                .arr(arr)
                .tcv(tcv)
                .acv(acv)
                .clv(clv)
                .churnScore(customer != null ? customer.getChurnScore() : null)
                .discountRate(discountRate)
                .totalDiscount(totalDiscount)
                .totalSurcharge(totalSurcharge)
                .priceMultiplier(priceMultiplier)
                .estimatedMarginPct(estimatedMarginPct)
                .build();
    }
}
