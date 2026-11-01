package com.opencrm.crm.organization;

import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sets or clears the parent of an organization. A null parent unlinks the account from its parent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LinkToParentRequest {

    @Positive(message = "Parent organization id must be positive")
    private Long parentOrganizationId;
}
