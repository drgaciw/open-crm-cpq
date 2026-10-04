package com.opencrm.pricing.execution;

import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final AcvVersion acvVersion;

    /**
     * Legacy ACV. Kept so callers that construct the calculator directly, including the execute
     * goldens, stay on today's formula when no property is supplied.
     */
    public MetricsCalculator() {
        this(AcvVersion.LEGACY);
    }

    @Autowired
    public MetricsCalculator(AcvVersionProperties properties) {
        this(properties == null ? null : properties.getAcvVersion());
    }

    MetricsCalculator(AcvVersion acvVersion) {
        this.acvVersion = acvVersion == null ? AcvVersion.LEGACY : acvVersion;
    }

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
     *   <li>ACV = (monthlyPrice × durationInMonths) / 12 when {@link AcvVersion#LEGACY}
     *       (default, ADR-023 D-03). {@link AcvVersion#TERM_YEARS} is TCV / (durationInMonths / 12).
     *       The selected version is the same function for every quote.</li>
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

        // ACV version is ADR-023 D-03. Legacy stays (monthlyPrice * duration) / 12.
        BigDecimal acv = acvFor(monthlyPrice, tcv, effectiveDurationInMonths);

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

    /**
     * One ACV function for every quote. {@code term-years} is {@code TCV / (months / 12)},
     * evaluated as {@code TCV × 12 / months} so a non-divisible term does not truncate.
     */
    private BigDecimal acvFor(BigDecimal monthlyPrice, BigDecimal tcv, int durationInMonths) {
        if (acvVersion == AcvVersion.TERM_YEARS) {
            return tcv.multiply(BigDecimal.valueOf(12))
                    .divide(BigDecimal.valueOf(durationInMonths), MONEY_SCALE, MONEY_ROUNDING);
        }
        return monthlyPrice
                .multiply(BigDecimal.valueOf(durationInMonths))
                .divide(BigDecimal.valueOf(12), MONEY_SCALE, MONEY_ROUNDING);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
