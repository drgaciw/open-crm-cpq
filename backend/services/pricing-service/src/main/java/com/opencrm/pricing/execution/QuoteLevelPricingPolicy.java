package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Applies quote-level policies that depend on derived financial metrics.
 */
@Component
@RequiredArgsConstructor
public class QuoteLevelPricingPolicy {

    private static final BigDecimal ACV_MINIMUM_COMMITMENT = new BigDecimal("50000.00");
    private static final List<TcvTier> TCV_TIERS = List.of(
            new TcvTier(new BigDecimal("500000.00"), new BigDecimal("0.12"), "TCV_TIER_12PCT"),
            new TcvTier(new BigDecimal("250000.00"), new BigDecimal("0.08"), "TCV_TIER_8PCT"),
            new TcvTier(new BigDecimal("50000.00"), new BigDecimal("0.05"), "TCV_TIER_5PCT")
    );

    private final MetricsCalculator metricsCalculator;

    public void apply(PricingResult result, int durationInMonths) {
        if (result == null || result.getLineResults() == null || result.getLineResults().isEmpty()) {
            return;
        }

        ensureExplanationCodes(result);
        applyTcvTier(result);
        result.setFinancialMetrics(metricsCalculator.calculate(result, durationInMonths));

        applyAcvMinimumCommitment(result, durationInMonths);
        result.setFinancialMetrics(metricsCalculator.calculate(result, durationInMonths));
    }

    private void applyTcvTier(PricingResult result) {
        FinancialMetrics metrics = result.getFinancialMetrics();
        if (metrics == null || metrics.getTcv() == null) {
            return;
        }

        TcvTier matchingTier = TCV_TIERS.stream()
                .filter(tier -> metrics.getTcv().compareTo(tier.threshold()) >= 0)
                .max(Comparator.comparing(TcvTier::threshold))
                .orElse(null);

        if (matchingTier == null) {
            return;
        }

        BigDecimal multiplier = BigDecimal.ONE.subtract(matchingTier.discountRate());
        for (PricingLineResult line : result.getLineResults()) {
            BigDecimal currentFinalPrice = line.getFinalPrice() != null ? line.getFinalPrice() : BigDecimal.ZERO;
            BigDecimal adjustedFinalPrice = currentFinalPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            BigDecimal discountAmount = currentFinalPrice.subtract(adjustedFinalPrice).setScale(2, RoundingMode.HALF_UP);

            line.setFinalPrice(adjustedFinalPrice);
            line.setDiscountAmount(add(line.getDiscountAmount(), discountAmount));
            ensureExplanationCodes(line);
            addExplanationCode(line.getExplanationCodes(), matchingTier.explanationCode());
        }
        addExplanationCode(result.getExplanationCodes(), matchingTier.explanationCode());
    }

    private void applyAcvMinimumCommitment(PricingResult result, int durationInMonths) {
        FinancialMetrics metrics = result.getFinancialMetrics();
        if (metrics == null || metrics.getAcv() == null
                || metrics.getAcv().compareTo(ACV_MINIMUM_COMMITMENT) >= 0) {
            return;
        }

        BigDecimal monthlyFloor = ACV_MINIMUM_COMMITMENT
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        BigDecimal currentMonthlyTotal = monthlyTotal(result);
        BigDecimal floorDelta = monthlyFloor.subtract(currentMonthlyTotal);
        if (floorDelta.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        PricingLineResult targetLine = result.getLineResults().get(0);
        BigDecimal currentFinalPrice = targetLine.getFinalPrice() != null
                ? targetLine.getFinalPrice()
                : BigDecimal.ZERO;
        BigDecimal adjustedFinalPrice = currentFinalPrice.add(floorDelta).setScale(2, RoundingMode.HALF_UP);

        targetLine.setFinalPrice(adjustedFinalPrice);
        targetLine.setSurchargeAmount(add(targetLine.getSurchargeAmount(), floorDelta.setScale(2, RoundingMode.HALF_UP)));
        ensureExplanationCodes(targetLine);
        addExplanationCode(targetLine.getExplanationCodes(), "ACV_MIN_COMMIT_50000");
        addExplanationCode(result.getExplanationCodes(), "ACV_MIN_COMMIT_50000");
    }

    private BigDecimal monthlyTotal(PricingResult result) {
        return result.getLineResults().stream()
                .map(PricingLineResult::getFinalPrice)
                .map(value -> value != null ? value : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal add(BigDecimal left, BigDecimal right) {
        BigDecimal safeLeft = left != null ? left : BigDecimal.ZERO;
        BigDecimal safeRight = right != null ? right : BigDecimal.ZERO;
        return safeLeft.add(safeRight).setScale(2, RoundingMode.HALF_UP);
    }

    private void ensureExplanationCodes(PricingResult result) {
        if (result.getExplanationCodes() == null) {
            result.setExplanationCodes(new ArrayList<>());
        }
    }

    private void ensureExplanationCodes(PricingLineResult line) {
        if (line.getExplanationCodes() == null) {
            line.setExplanationCodes(new ArrayList<>());
        }
    }

    private void addExplanationCode(List<String> explanationCodes, String explanationCode) {
        if (!explanationCodes.contains(explanationCode)) {
            explanationCodes.add(explanationCode);
        }
    }

    private record TcvTier(BigDecimal threshold, BigDecimal discountRate, String explanationCode) {
    }
}
