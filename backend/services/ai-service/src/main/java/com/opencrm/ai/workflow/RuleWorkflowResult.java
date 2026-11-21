package com.opencrm.ai.workflow;

import java.util.List;
import java.util.Map;

/**
 * Advisory result for create / modify / optimize rule workflows.
 * Never activates a rule set.
 */
public record RuleWorkflowResult(
        boolean success,
        boolean requiresApproval,
        String businessRequirement,
        String agentOutput,
        String proposedDrl,
        Long draftRuleId,
        boolean draftPersisted,
        boolean draftValidated,
        String validationMessage,
        String errorMessage,
        List<String> suggestions,
        Map<String, Object> metadata
) {
    public static RuleWorkflowResult failure(String requirement, String error) {
        return new RuleWorkflowResult(
                false, true, requirement, null, null, null, false, false, null, error, List.of(), Map.of());
    }
}
