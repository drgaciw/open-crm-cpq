package com.opencrm.ai.pricing;

import java.util.List;

public record RulesOptimizationResponse(
        String ruleId,
        String optimizedRuleContent,
        List<String> recommendations,
        List<String> warnings,
        boolean readyForDirectPricingMutation
) {
}
