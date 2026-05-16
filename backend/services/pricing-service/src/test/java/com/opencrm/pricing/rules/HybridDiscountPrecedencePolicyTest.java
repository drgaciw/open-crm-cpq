package com.opencrm.pricing.rules;

import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RuleExecutionTraceEntry;
import com.opencrm.rules.engine.RulesEngineType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridDiscountPrecedencePolicyTest {

    private final HybridDiscountPrecedencePolicy policy = new HybridDiscountPrecedencePolicy();

    @Test
    void compoundsOpenLAndDroolsDiscountsWithoutDoubleCountingOnSameBase() {
        PricingLineResult line = PricingLineResult.builder()
                .productExternalId("PROD-001")
                .quantity(1)
                .listPrice(BigDecimal.valueOf(100))
                .unitPrice(BigDecimal.valueOf(70))
                .discountRate(BigDecimal.valueOf(0.30))
                .discountAmount(BigDecimal.valueOf(30))
                .finalPrice(BigDecimal.valueOf(70))
                .build();

        RuleExecutionResult ruleResult = RuleExecutionResult.builder()
                .success(true)
                .trace(List.of(
                        trace(RulesEngineType.OPENL_TABLETS, "volume-table", "discountRate=0.10"),
                        trace(RulesEngineType.DROOLS, "deal-discount", "discountRate=0.20")))
                .build();

        policy.enforce(List.of(line), ruleResult);

        assertThat(line.getDiscountRate()).isEqualByComparingTo("0.28");
        assertThat(line.getDiscountAmount()).isEqualByComparingTo("28");
        assertThat(line.getFinalPrice()).isEqualByComparingTo("72");
    }

    @Test
    void leavesLinesUntouchedWhenOnlyOneEngineAppliedDiscount() {
        PricingLineResult line = PricingLineResult.builder()
                .listPrice(BigDecimal.valueOf(100))
                .unitPrice(BigDecimal.valueOf(90))
                .discountRate(BigDecimal.valueOf(0.10))
                .discountAmount(BigDecimal.valueOf(10))
                .finalPrice(BigDecimal.valueOf(90))
                .quantity(1)
                .build();

        RuleExecutionResult ruleResult = RuleExecutionResult.builder()
                .success(true)
                .trace(List.of(trace(RulesEngineType.OPENL_TABLETS, "volume-table", "discountRate=0.10")))
                .build();

        policy.enforce(List.of(line), ruleResult);

        assertThat(line.getDiscountRate()).isEqualByComparingTo("0.10");
        assertThat(line.getFinalPrice()).isEqualByComparingTo("90");
    }

    @Test
    void sequentialCompoundMatchesExpectedPrecedenceFormula() {
        BigDecimal compounded = policy.compoundSequential(
                new BigDecimal("0.10"),
                new BigDecimal("0.20"));

        assertThat(compounded).isEqualByComparingTo("0.28");
    }

    private static RuleExecutionTraceEntry trace(
            RulesEngineType engineType,
            String ruleId,
            String rateDescriptor
    ) {
        return RuleExecutionTraceEntry.builder()
                .sequence(engineType == RulesEngineType.OPENL_TABLETS ? 1 : 2)
                .engineType(engineType)
                .ruleId(ruleId + ":" + rateDescriptor)
                .ruleName(ruleId)
                .build();
    }
}
