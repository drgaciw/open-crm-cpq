package com.opencrm.pricing.execution;

import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Quote Level Metrics Policy")
class QuoteLevelMetricsPolicyTest {

    private final QuoteLevelMetricsPolicy policy = new QuoteLevelMetricsPolicy();

    @Test
    @DisplayName("applies TCV tier discount after line pricing")
    void appliesTcvTierDiscount() {
        PricingResult input = baselineResult(
                BigDecimal.valueOf(120000),
                BigDecimal.valueOf(15000),
                BigDecimal.valueOf(9000));

        PricingResult output = policy.apply(input, 12);

        assertTrue(output.getExplanationCodes().contains(QuoteLevelMetricsPolicy.TCV_TIER_APPLIED));
        assertTrue(output.getLineResults().get(0).getDiscountAmount().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("enforces ACV minimum after TCV tier adjustments")
    void enforcesAcvMinimum() {
        PricingResult input = baselineResult(
                BigDecimal.valueOf(5000),
                BigDecimal.valueOf(4500),
                BigDecimal.valueOf(1));

        PricingResult output = policy.apply(input, 1);

        assertTrue(output.getExplanationCodes().contains(QuoteLevelMetricsPolicy.ACV_MINIMUM_ENFORCED));
        assertTrue(output.getFinancialMetrics().getAcv().compareTo(BigDecimal.valueOf(12000)) >= 0);
    }

    @Test
    @DisplayName("records write-order conflict when both additive policies run")
    void recordsWriteOrderConflict() {
        PricingResult input = baselineResult(
                BigDecimal.valueOf(120000),
                BigDecimal.valueOf(2000),
                BigDecimal.valueOf(1));

        PricingResult output = policy.apply(input, 1);

        assertTrue(output.getExplanationCodes().contains(QuoteLevelMetricsPolicy.TCV_TIER_APPLIED));
        assertTrue(output.getExplanationCodes().contains(QuoteLevelMetricsPolicy.ACV_MINIMUM_ENFORCED));
        assertTrue(output.getExplanationCodes().contains(QuoteLevelMetricsPolicy.WRITE_ORDER_CONFLICT));
    }

    private static PricingResult baselineResult(BigDecimal tcv, BigDecimal acv, BigDecimal monthlyTotal) {
        PricingLineResult line = PricingLineResult.builder()
                .productExternalId("PROD-1")
                .quantity(1)
                .listPrice(monthlyTotal)
                .finalPrice(monthlyTotal)
                .discountAmount(BigDecimal.ZERO)
                .surchargeAmount(BigDecimal.ZERO)
                .build();

        FinancialMetrics metrics = FinancialMetrics.builder()
                .arr(monthlyTotal.multiply(BigDecimal.valueOf(12)))
                .tcv(tcv)
                .acv(acv)
                .totalDiscount(BigDecimal.ZERO)
                .build();

        return PricingResult.builder()
                .success(true)
                .lineResults(List.of(line))
                .financialMetrics(metrics)
                .build();
    }
}
