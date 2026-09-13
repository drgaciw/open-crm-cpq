package com.opencrm.pricing;

import com.opencrm.pricing.customer.CustomerSegment;
import com.opencrm.pricing.product.ProductCategory;
import com.opencrm.pricing.product.ProductType;
import com.opencrm.pricing.rule.RuleStatus;
import com.opencrm.pricing.rule.RuleType;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetStatus;
import com.opencrm.pricing.waterfall.WaterfallStepType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pricing enums and RuleSet state")
class PricingEnumsAndEntityTest {

    @Test
    void enums_haveStableNames() {
        assertThat(ProductType.values()).hasSizeGreaterThan(10);
        assertThat(ProductType.valueOf("SAAS_SEAT_NAMED")).isEqualTo(ProductType.SAAS_SEAT_NAMED);
        assertThat(WaterfallStepType.values()).contains(WaterfallStepType.NET_PRICE, WaterfallStepType.TAX);
        assertThat(RuleType.values()).contains(RuleType.PRICING, RuleType.APPROVAL);
        assertThat(RuleStatus.values()).contains(RuleStatus.DRAFT, RuleStatus.ACTIVE);
        assertThat(CustomerSegment.values()).isNotEmpty();
        assertThat(ProductCategory.values()).isNotEmpty();
        assertThat(RuleSetStatus.values()).contains(RuleSetStatus.DRAFT, RuleSetStatus.ACTIVE, RuleSetStatus.DEPRECATED);
    }

    @Test
    void ruleSet_activateAndDeprecate() {
        RuleSet set = RuleSet.builder().name("s").status(RuleSetStatus.DRAFT).build();
        set.activate();
        assertThat(set.getStatus()).isEqualTo(RuleSetStatus.ACTIVE);
        set.deprecate();
        assertThat(set.getStatus()).isEqualTo(RuleSetStatus.DEPRECATED);
    }

    @Test
    void ruleSet_activateRejectsNonDraft() {
        RuleSet set = RuleSet.builder().name("s").status(RuleSetStatus.ACTIVE).build();
        assertThatThrownBy(set::activate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ruleSet_deprecateRejectsNonActive() {
        RuleSet set = RuleSet.builder().name("s").status(RuleSetStatus.DRAFT).build();
        assertThatThrownBy(set::deprecate).isInstanceOf(IllegalStateException.class);
    }
}
