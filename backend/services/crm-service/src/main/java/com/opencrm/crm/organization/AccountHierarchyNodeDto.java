package com.opencrm.crm.organization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One organization on a hierarchy chain.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountHierarchyNodeDto {

    private Long id;
    private String accountId;
    private String name;
    private Long parentOrganizationId;
}
