package com.opencrm.pricing.rule;

import com.opencrm.pricing.ruleset.RuleSetStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Impact summary for a rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleImpactDto {

    private Long ruleId;
    private String ruleName;
    private RuleType ruleType;
    private RuleStatus ruleStatus;
    private Integer priority;
    private Long ruleSetId;
    private RuleSetStatus ruleSetStatus;
    private boolean activeRuleSet;
    private long totalRulesInRuleSet;
    private long activeRulesInRuleSet;
    private long pricingExecutionsWithRuleSet;
    private long pricingExecutionsWithRuleSetLast30Days;
}
