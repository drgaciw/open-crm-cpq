package com.opencrm.ai.workflow;

import java.util.Map;

/**
 * Request to optimize a rule or produce advisory price guidance.
 */
public record OptimizeRuleWorkflowRequest(
        Long ruleId,
        String ruleContent,
        Map<String, Object> pricingContext,
        boolean persistDraft
) {}
