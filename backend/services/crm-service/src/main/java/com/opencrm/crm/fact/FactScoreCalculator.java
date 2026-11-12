package com.opencrm.crm.fact;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Server-side evidence score from ADR-028 D-02: {@code 1 − Π(1 − wᵢ)}, capped at 0.99.
 * The model never supplies this number.
 */
public final class FactScoreCalculator {

    public static final BigDecimal CAP = new BigDecimal("0.99");
    private static final int SCALE = 4;

    private FactScoreCalculator() {
    }

    public static BigDecimal score(List<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("At least one evidence weight is required");
        }
        BigDecimal product = BigDecimal.ONE;
        for (BigDecimal weight : weights) {
            if (weight == null
                    || weight.compareTo(BigDecimal.ZERO) < 0
                    || weight.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Evidence weight must be between 0 and 1");
            }
            product = product.multiply(BigDecimal.ONE.subtract(weight));
        }
        BigDecimal score = BigDecimal.ONE.subtract(product);
        if (score.compareTo(CAP) > 0) {
            score = CAP;
        }
        return score.setScale(SCALE, RoundingMode.HALF_UP);
    }
}
