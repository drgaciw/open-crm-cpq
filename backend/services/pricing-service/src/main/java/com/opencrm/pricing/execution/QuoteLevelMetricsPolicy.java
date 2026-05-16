package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Quote-level additive metrics policies applied after line list price and explicit discounts.
 * Write order: line pricing -> TCV tier incentive -> ACV minimum commitment floor.
 */
@Component
public class QuoteLevelMetricsPolicy {

    public static final String TCV_TIER_APPLIED = "TCV_TIER_APPLIED";
    public static final String ACV_MINIMUM_ENFORCED = "ACV_MINIMUM_ENFORCED";
    public static final String WRITE_ORDER_CONFLICT = "WRITE_ORDER_CONFLICT";

    private static final BigDecimal TCV_TIER_THRESHOLD = new BigDecimal("100000");
    private static final BigDecimal TCV_TIER_DISCOUNT_RATE = new BigDecimal("0.02");
    private static final BigDecimal ACV_MINIMUM = new BigDecimal("12000");

    public PricingResult apply(PricingResult result, int durationInMonths) {
        if (result == null || result.getLineResults() == null || result.getLineResults().isEmpty()) {
            return result;
        }

        List<PricingLineResult> adjustedLines = new ArrayList<>(result.getLineResults());
        Set<String> explanationCodes = new LinkedHashSet<>();
        if (result.getExplanationCodes() != null) {
            explanationCodes.addAll(result.getExplanationCodes());
        }

        FinancialMetrics baselineMetrics = result.getFinancialMetrics();
        BigDecimal baselineTcv = baselineMetrics != null && baselineMetrics.getTcv() != null
                ? baselineMetrics.getTcv()
                : BigDecimal.ZERO;

        if (baselineTcv.compareTo(TCV_TIER_THRESHOLD) >= 0) {
            applyTcvTierDiscount(adjustedLines);
            explanationCodes.add(TCV_TIER_APPLIED);
        }

        FinancialMetrics afterTcv = recalculateMetrics(adjustedLines, durationInMonths, baselineMetrics);
        if (afterTcv.getAcv().compareTo(ACV_MINIMUM) < 0) {
            enforceAcvMinimum(adjustedLines, afterTcv.getAcv());
            explanationCodes.add(ACV_MINIMUM_ENFORCED);
        }

        FinancialMetrics finalMetrics = recalculateMetrics(adjustedLines, durationInMonths, baselineMetrics);
        if (explanationCodes.contains(TCV_TIER_APPLIED) && explanationCodes.contains(ACV_MINIMUM_ENFORCED)) {
            explanationCodes.add(WRITE_ORDER_CONFLICT);
        }

        return PricingResult.builder()
                .success(result.isSuccess())
                .lineResults(adjustedLines)
                .waterfall(result.getWaterfall())
                .financialMetrics(finalMetrics)
                .explanationCodes(new ArrayList<>(explanationCodes))
                .approvalState(result.getApprovalState())
                .approvalTier(result.getApprovalTier())
                .executionTimeMs(result.getExecutionTimeMs())
                .errorMessage(result.getErrorMessage())
                .build();
    }

    private void applyTcvTierDiscount(List<PricingLineResult> lines) {
        for (PricingLineResult line : lines) {
            BigDecimal finalPrice = line.getFinalPrice() != null ? line.getFinalPrice() : BigDecimal.ZERO;
            BigDecimal tierDiscount = finalPrice.multiply(TCV_TIER_DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
            line.setDiscountAmount(safe(line.getDiscountAmount()).add(tierDiscount));
            line.setFinalPrice(finalPrice.subtract(tierDiscount).max(BigDecimal.ZERO));
        }
    }

    private void enforceAcvMinimum(List<PricingLineResult> lines, BigDecimal currentAcv) {
        if (currentAcv.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal upliftFactor = ACV_MINIMUM.divide(currentAcv, 6, RoundingMode.HALF_UP);
        for (PricingLineResult line : lines) {
            BigDecimal finalPrice = line.getFinalPrice() != null ? line.getFinalPrice() : BigDecimal.ZERO;
            BigDecimal uplifted = finalPrice.multiply(upliftFactor).setScale(2, RoundingMode.HALF_UP);
            BigDecimal upliftAmount = uplifted.subtract(finalPrice);
            line.setSurchargeAmount(safe(line.getSurchargeAmount()).add(upliftAmount));
            line.setFinalPrice(uplifted);
        }
    }

    private FinancialMetrics recalculateMetrics(
            List<PricingLineResult> lines,
            int durationInMonths,
            FinancialMetrics template
    ) {
        PricingResult interim = PricingResult.builder().lineResults(lines).build();
        MetricsCalculator calculator = new MetricsCalculator();
        FinancialMetrics metrics = calculator.calculate(interim, durationInMonths);
        if (template != null && template.getChurnScore() != null) {
            return FinancialMetrics.builder()
                    .arr(metrics.getArr())
                    .tcv(metrics.getTcv())
                    .acv(metrics.getAcv())
                    .clv(metrics.getClv())
                    .churnScore(template.getChurnScore())
                    .discountRate(metrics.getDiscountRate())
                    .totalDiscount(metrics.getTotalDiscount())
                    .totalSurcharge(metrics.getTotalSurcharge())
                    .priceMultiplier(metrics.getPriceMultiplier())
                    .estimatedMarginPct(metrics.getEstimatedMarginPct())
                    .build();
        }
        return metrics;
    }

    private static BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
