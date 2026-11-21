package com.opencrm.ai.workflow;

import java.util.List;
import java.util.Map;

/**
 * Advisory evaluation of a rule set.
 */
public record RuleEvaluationWorkflowResult(
        boolean success,
        Long ruleSetId,
        String agentOutput,
        List<String> findings,
        String errorMessage,
        Map<String, Object> impactSummary
) {
    public static RuleEvaluationWorkflowResult failure(Long ruleSetId, String error) {
        return new RuleEvaluationWorkflowResult(false, ruleSetId, null, List.of(), error, Map.of());
    }
}
