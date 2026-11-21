package com.opencrm.ai.workflow;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to propose a modification to an existing pricing rule.
 */
public record ModifyRuleWorkflowRequest(
        @NotBlank String modificationRequirement,
        boolean persistDraft
) {}
