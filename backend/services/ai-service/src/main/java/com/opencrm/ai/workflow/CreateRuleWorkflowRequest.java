package com.opencrm.ai.workflow;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to draft a pricing rule from a business requirement.
 */
public record CreateRuleWorkflowRequest(
        @NotBlank String businessRequirement,
        String ruleType,
        String businessDomain,
        Long ruleSetId,
        String ruleName,
        Integer priority,
        boolean persistDraft
) {
    public CreateRuleWorkflowRequest {
        if (ruleType == null || ruleType.isBlank()) {
            ruleType = "PRICING";
        }
        if (priority == null) {
            priority = 500;
        }
    }
}
