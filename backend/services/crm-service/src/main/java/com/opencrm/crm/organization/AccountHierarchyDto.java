package com.opencrm.crm.organization;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Resolved account hierarchy for one organization.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountHierarchyDto {

    private Long organizationId;
    private String accountId;
    private Long parentOrganizationId;

    /**
     * Account UUIDs of ancestors, nearest parent first and the root last.
     * Later eligibility and contracted pricing can walk this list to inherit from an ancestor.
     */
    private List<String> ancestorIds;

    /**
     * Root-first chain, including this organization as the last node.
     */
    private List<AccountHierarchyNodeDto> chain;
}
