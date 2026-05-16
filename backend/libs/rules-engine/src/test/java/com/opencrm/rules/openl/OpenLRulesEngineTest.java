package com.opencrm.rules.openl;

import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class OpenLRulesEngineTest {

    private final OpenLRulesEngine engine = new OpenLRulesEngine();

    @Test
    void appliesOpenLTableDiscountDescriptorToLineFacts() {
        TestLineFact line = new TestLineFact(BigDecimal.valueOf(100), BigDecimal.ZERO);
        RuleExecutionContext context = RuleExecutionContext.builder()
                .ruleSetId("pricing")
                .build();
        context.getFacts().add(line);

        RuleDefinition rule = RuleDefinition.builder()
                .id("volume-discount")
                .name("Volume Discount")
                .content("openl-table:discountRate=0.10")
                .priority(100)
                .engineType("OPENL_TABLETS")
                .build();

        RuleExecutionResult result = engine.execute(context, List.of(rule));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getRulesFired()).isEqualTo(1);
        assertThat(line.discountRate).isEqualByComparingTo("0.10");
        assertThat(result.getOutputs()).containsEntry("openl.discountRate", new BigDecimal("0.10"));
    }

    static final class TestLineFact {
        private final BigDecimal listPrice;
        private BigDecimal discountRate;
        private BigDecimal discountAmount;
        private BigDecimal unitPrice;

        TestLineFact(BigDecimal listPrice, BigDecimal discountRate) {
            this.listPrice = listPrice;
            this.discountRate = discountRate;
        }

        public BigDecimal getListPrice() {
            return listPrice;
        }

        public BigDecimal getDiscountRate() {
            return discountRate;
        }

        public void setDiscountRate(BigDecimal discountRate) {
            this.discountRate = discountRate;
        }

        public void setDiscountAmount(BigDecimal discountAmount) {
            this.discountAmount = discountAmount;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }
    }
}
