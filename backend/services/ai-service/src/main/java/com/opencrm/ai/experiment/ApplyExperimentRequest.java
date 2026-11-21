package com.opencrm.ai.experiment;

import jakarta.validation.constraints.NotBlank;

/**
 * Apply winning variant as a DRAFT rule. Requires DEAL_DESK or ADMIN.
 */
public record ApplyExperimentRequest(
        @NotBlank String approvedBy
) {
}
