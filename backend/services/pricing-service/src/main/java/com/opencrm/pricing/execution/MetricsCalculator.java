package com.opencrm.pricing.execution;

import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Derives quote financial metrics in Java (hybrid §5.1a).
 *
 * <p>Source {@code Calculate ARR/TCV/ACV/CLV} DRL rules in pricing-ai-rules-service
 * {@code PricingRules.drl} @ {@code 3aa45bf} (and twin
 * {@code FinancialMetricsServiceImpl}) are the SPEC for these formulas — they are
 * <strong>not</strong> ported as DRL. Policy that consumes metrics stays in Drools (T1-3).
 */
@Component
public class MetricsCalculator {

    private static final int MONEY_SCALE = 2;
    private static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    /**
     * Calculate financial metrics from pricing result.
     */
    public FinancialMetrics calculate(PricingResult result, int durationInMonths) {
        return calculate(result, durationInMonths, null, null);
    }

    /**
     * Calculate financial metrics from pricing result and customer context.
     * CLV uses {@code durationInMonths * 2} as expected-lifetime proxy when no explicit duration is supplied.
     */
    public FinancialMetrics calculate(PricingResult result, int durationInMonths, Customer customer) {
        return calculate(result, durationInMonths, customer, null);
    }

    /**
     * Calculate financial metrics with optional explicit CLV lifetime months.
     *
     * <p>SPEC (source Calculate-* / {@code FinancialMetricsServiceImpl}):
     * <ul>
     *   <li>ARR = monthlyPrice × 12</li>
     *   <li>TCV = monthlyPrice × durationInMonths</li>
     *   <li>ACV = (monthlyPrice × durationInMonths) / 12</li>
     *   <li>CLV = monthlyPrice × expectedDuration (else duration × 2 proxy)</li>
     * </ul>
     *
     * @param expectedDurationMonths source {@code Quote.expectedDuration}; null → duration × 2 proxy
     */
    public FinancialMetrics calculate(
            PricingResult result,
            int durationInMonths,
            Customer customer,
            Integer expectedDurationMonths) {
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

        // Sum of line final prices is the quote monthlyPrice basis (execute waterfall).
        BigDecimal monthlyPrice = result.getLineResults().stream()
                .map(line -> line.getFinalPrice() != null ? line.getFinalPrice() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal listPrice = result.getLineResults().stream()
                .map(line -> line.getListPrice() != null
                        ? line.getListPrice().multiply(BigDecimal.valueOf(line.getQuantity() != null ? line.getQuantity() : 0))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ARR = monthlyPrice * 12 (source Calculate ARR)
        BigDecimal arr = money(monthlyPrice.multiply(BigDecimal.valueOf(12)));

        // TCV = monthlyPrice * durationInMonths (source Calculate TCV)
        BigDecimal tcv = money(monthlyPrice.multiply(BigDecimal.valueOf(effectiveDurationInMonths)));

        // ACV = (monthlyPrice * duration) / 12 (source Calculate ACV)
        BigDecimal acv = monthlyPrice
                .multiply(BigDecimal.valueOf(effectiveDurationInMonths))
                .divide(BigDecimal.valueOf(12), MONEY_SCALE, MONEY_ROUNDING);

        // CLV = monthlyPrice * expectedDuration (source Calculate CLV); duration×2 proxy when unset
        int clvDurationMonths = expectedDurationMonths != null && expectedDurationMonths > 0
                ? expectedDurationMonths
                : effectiveDurationInMonths * 2;
        BigDecimal clv = money(monthlyPrice.multiply(BigDecimal.valueOf(clvDurationMonths)));

        BigDecimal totalDiscount = result.getLineResults().stream()
                .map(line -> line.getDiscountAmount() != null ? line.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSurcharge = result.getLineResults().stream()
                .map(line -> line.getSurchargeAmount() != null ? line.getSurchargeAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discountRate = listPrice.compareTo(BigDecimal.ZERO) > 0
                ? totalDiscount.divide(listPrice, 4, MONEY_ROUNDING)
                : BigDecimal.ZERO;

        BigDecimal priceMultiplier = listPrice.compareTo(BigDecimal.ZERO) > 0
                ? monthlyPrice.divide(listPrice, 4, MONEY_ROUNDING)
                : BigDecimal.ZERO;

        // Estimated margin (placeholder - would need cost data; matrix M5 defer)
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

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
