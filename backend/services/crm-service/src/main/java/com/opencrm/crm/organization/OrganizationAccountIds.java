package com.opencrm.crm.organization;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;

/** Resolves a legacy numeric REST identifier to the organization's shared UUID identity. */
public final class OrganizationAccountIds {
    private OrganizationAccountIds() {}

    public static AccountId fromExternalId(Long id, OrganizationRepository organizations) {
        Organization organization = organizations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        return AccountId.of(organization.getUuid());
    }
}
