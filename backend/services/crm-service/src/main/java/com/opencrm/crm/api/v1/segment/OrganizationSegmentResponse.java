package com.opencrm.crm.api.v1.segment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published GetSegment v1 read model.
 * {@code segment} is null when the organization is unassigned.
 * {@code organizationId} is the account UUID other bounded contexts pass.
 * Fields are scalars. This type does not expose a CRM entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "OrganizationSegmentResponse",
        description = "GetSegment v1. organizationId is the account UUID. "
                + "segment, territory and ruleId are null when assigned is false.")
public class OrganizationSegmentResponse {

    @Schema(description = "Account UUID", example = "11111111-1111-1111-1111-111111111111")
    private String organizationId;

    @Schema(description = "Assigned territory code, or null when unassigned", nullable = true)
    private String territory;

    @Schema(description = "Assigned segment code, or null when unassigned", nullable = true)
    private String segment;

    @Schema(description = "Matching rule id, or null when unassigned", nullable = true)
    private Long ruleId;

    @Schema(description = "True when a territory and segment are assigned")
    private boolean assigned;
}
