package com.opencrm.ai.pricing;

import java.util.List;
import java.util.Map;

public record RulesOptimizationRequest(
        String ruleId,
        String draftRuleContent,
        String optimizationGoal,
        Map<String, Object> evaluationContext,
        List<String> policyReferences
) {
}
