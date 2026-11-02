package com.opencrm.crm.territory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GetSegment read model. {@code segment} is null when the organization is unassigned.
 * {@code organizationId} is the account UUID other bounded contexts will pass.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationSegmentResponse {

    private String organizationId;
    private String territory;
    private String segment;
    private Long ruleId;
    private boolean assigned;
}
