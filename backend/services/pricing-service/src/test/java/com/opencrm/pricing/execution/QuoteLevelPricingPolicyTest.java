package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Quote Level Pricing Policy")
class QuoteLevelPricingPolicyTest {

    private final MetricsCalculator metricsCalculator = new MetricsCalculator();
    private final QuoteLevelPricingPolicy policy = new QuoteLevelPricingPolicy(metricsCalculator);

    @Test
    @DisplayName("should apply TCV pricing tier before recalculating financial metrics")
    void shouldApplyTcvPricingTier() {
        PricingResult result = resultWithMonthlyPrices(new BigDecimal("10000.00"));
        result.setFinancialMetrics(metricsCalculator.calculate(result, 24));

        policy.apply(result, 24);

        assertEquals(0, new BigDecimal("9500.00").compareTo(result.getLineResults().get(0).getFinalPrice()));
        assertEquals(0, new BigDecimal("228000.00").compareTo(result.getFinancialMetrics().getTcv()));
        assertTrue(result.getExplanationCodes().contains("TCV_TIER_5PCT"));
    }

    @Test
    @DisplayName("should apply ACV minimum commitment after TCV tier discounts")
    void shouldApplyAcvMinimumCommitmentAfterTcvTier() {
        PricingResult result = resultWithMonthlyPrices(new BigDecimal("4250.00"));
        result.setFinancialMetrics(metricsCalculator.calculate(result, 12));

        policy.apply(result, 12);

        assertEquals(0, new BigDecimal("4166.67").compareTo(result.getLineResults().get(0).getFinalPrice()));
        assertEquals(0, new BigDecimal("50000.04").compareTo(result.getFinancialMetrics().getAcv()));
        assertTrue(result.getExplanationCodes().contains("TCV_TIER_5PCT"));
        assertTrue(result.getExplanationCodes().contains("ACV_MIN_COMMIT_50000"));
    }

    private PricingResult resultWithMonthlyPrices(BigDecimal monthlyPrice) {
        return PricingResult.builder()
                .success(true)
                .lineResults(List.of(PricingLineResult.builder()
                        .productExternalId("PROD-001")
                        .quantity(1)
                        .listPrice(monthlyPrice)
                        .unitPrice(monthlyPrice)
                        .discountRate(BigDecimal.ZERO)
                        .discountAmount(BigDecimal.ZERO)
                        .surchargeAmount(BigDecimal.ZERO)
                        .finalPrice(monthlyPrice)
                        .explanationCodes(new ArrayList<>())
                        .build()))
                .explanationCodes(new ArrayList<>())
                .approvalState("APPROVED")
                .build();
    }
}
