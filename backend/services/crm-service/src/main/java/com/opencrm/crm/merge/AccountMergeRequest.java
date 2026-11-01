package com.opencrm.crm.merge;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The caller names both organizations. {@code survivorId} is the organization that remains.
 * These are CRM numeric ids, the same identifiers as {@code /api/v1/organizations/{id}}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountMergeRequest {

    @NotNull(message = "Survivor organization id is required")
    private Long survivorId;

    @NotNull(message = "Merged-away organization id is required")
    private Long mergedAwayId;
}
