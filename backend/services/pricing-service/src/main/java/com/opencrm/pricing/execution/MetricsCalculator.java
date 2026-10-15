package com.opencrm.pricing.execution;

import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.execution.margin.MarginAssessment;
import com.opencrm.pricing.execution.margin.MarginSource;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

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
    private static final int MARGIN_SCALE = 4;

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

        if (isRamp(result)) {
            return rampMetrics(result, effectiveDurationInMonths, customer, expectedDurationMonths);
        }

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

        // Placeholder margin, a non-authoritative percent (ADR-023 D-06). Under
        // opencrm.pricing.margin.source=cost-basis, applyCostBasisMargin replaces it (DRG-438).
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
     * Ramp TCV is the sum of period amounts. {@code arr} is the first period's monthly × 12 and
     * {@code exitArr} is the last. CLV scales that TCV by the lifetime ratio, not a flat monthly proxy.
     */
    private FinancialMetrics rampMetrics(
            PricingResult result,
            int durationInMonths,
            Customer customer,
            Integer expectedDurationMonths) {
        BigDecimal tcv = BigDecimal.ZERO;
        BigDecimal firstMonthly = BigDecimal.ZERO;
        BigDecimal lastMonthly = BigDecimal.ZERO;
        for (PricingLineResult line : result.getLineResults()) {
            List<PricedPeriod> periods = line.getPricedPeriods();
            if (periods == null || periods.isEmpty()) {
                BigDecimal monthly = line.getFinalPrice() != null ? line.getFinalPrice() : BigDecimal.ZERO;
                tcv = tcv.add(monthly.multiply(BigDecimal.valueOf(durationInMonths)));
                firstMonthly = firstMonthly.add(monthly);
                lastMonthly = lastMonthly.add(monthly);
                continue;
            }
            for (PricedPeriod period : periods) {
                tcv = tcv.add(period.getPeriodAmount() != null ? period.getPeriodAmount() : BigDecimal.ZERO);
            }
            firstMonthly = firstMonthly.add(monthlyOf(periods.get(0)));
            lastMonthly = lastMonthly.add(monthlyOf(periods.get(periods.size() - 1)));
        }
        tcv = money(tcv);
        BigDecimal arr = money(firstMonthly.multiply(BigDecimal.valueOf(12)));
        BigDecimal exitArr = money(lastMonthly.multiply(BigDecimal.valueOf(12)));
        BigDecimal acv = acvFromTcv(tcv, durationInMonths);

        int clvDurationMonths = expectedDurationMonths != null && expectedDurationMonths > 0
                ? expectedDurationMonths
                : durationInMonths * 2;
        BigDecimal clv = tcv.multiply(BigDecimal.valueOf(clvDurationMonths))
                .divide(BigDecimal.valueOf(durationInMonths), MONEY_SCALE, MONEY_ROUNDING);

        BigDecimal totalDiscount = result.getLineResults().stream()
                .map(line -> line.getDiscountAmount() != null ? line.getDiscountAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSurcharge = result.getLineResults().stream()
                .map(line -> line.getSurchargeAmount() != null ? line.getSurchargeAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal listPrice = result.getLineResults().stream()
                .map(line -> line.getListPrice() != null
                        ? line.getListPrice().multiply(BigDecimal.valueOf(line.getQuantity() != null ? line.getQuantity() : 0))
                        : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountRate = listPrice.compareTo(BigDecimal.ZERO) > 0
                ? totalDiscount.divide(listPrice, 4, MONEY_ROUNDING)
                : BigDecimal.ZERO;
        BigDecimal priceMultiplier = listPrice.compareTo(BigDecimal.ZERO) > 0
                ? firstMonthly.divide(listPrice, 4, MONEY_ROUNDING)
                : BigDecimal.ZERO;

        return FinancialMetrics.builder()
                .arr(arr)
                .tcv(tcv)
                .acv(acv)
                .exitArr(exitArr)
                .clv(clv)
                .churnScore(customer != null ? customer.getChurnScore() : null)
                .discountRate(discountRate)
                .totalDiscount(totalDiscount)
                .totalSurcharge(totalSurcharge)
                .priceMultiplier(priceMultiplier)
                .estimatedMarginPct(BigDecimal.valueOf(40.0))
                .build();
    }

    /**
     * Finance decision F-1: {@code (net − cost) / net}, a fraction at scale 4.
     *
     * <p>Rounds toward negative infinity so a margin just under the 0.35 floor can never round up onto it.
     * Returns null when the net is not positive or either input is missing: there is no margin then.
     */
    public static BigDecimal marginFraction(BigDecimal net, BigDecimal cost) {
        if (net == null || cost == null || net.signum() <= 0) {
            return null;
        }
        return net.subtract(cost).divide(net, MARGIN_SCALE, RoundingMode.FLOOR);
    }

    /**
     * Replaces the placeholder margin with the cost-basis margin (DRG-438). {@code estimatedMarginPct} keeps its
     * percent meaning, {@code marginFraction} carries the fraction, and both are null when a cost is missing:
     * the 40.0 placeholder never survives in cost-basis mode.
     */
    public FinancialMetrics applyCostBasisMargin(FinancialMetrics metrics, MarginAssessment assessment) {
        BigDecimal fraction = assessment == null ? null : assessment.getQuoteMarginFraction();
        metrics.setMarginFraction(fraction);
        metrics.setEstimatedMarginPct(fraction == null ? null
                : fraction.multiply(BigDecimal.valueOf(100)).setScale(MONEY_SCALE, RoundingMode.FLOOR));
        metrics.setMarginSource(MarginSource.COST_BASIS.wireValue());
        return metrics;
    }

    private BigDecimal acvFromTcv(BigDecimal tcv, int durationInMonths) {
        if (acvVersion == AcvVersion.TERM_YEARS) {
            return tcv.multiply(BigDecimal.valueOf(12))
                    .divide(BigDecimal.valueOf(durationInMonths), MONEY_SCALE, MONEY_ROUNDING);
        }
        return tcv.divide(BigDecimal.valueOf(12), MONEY_SCALE, MONEY_ROUNDING);
    }

    private static boolean isRamp(PricingResult result) {
        return result.getLineResults().stream().anyMatch(line ->
                line.getPricedPeriods() != null && !line.getPricedPeriods().isEmpty());
    }

    private static BigDecimal monthlyOf(PricedPeriod period) {
        return period.getNetMonthly() != null ? period.getNetMonthly() : BigDecimal.ZERO;
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
