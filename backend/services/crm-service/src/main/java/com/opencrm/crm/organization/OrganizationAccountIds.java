package com.opencrm.crm.organization;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;

/** Converts external organization identifiers while preserving the API's not-found response. */
public final class OrganizationAccountIds {
    private OrganizationAccountIds() {}

    public static AccountId fromExternalId(Long id) {
        if (id != null && id <= 0) {
            throw new ResourceNotFoundException("Organization", id);
        }
        return AccountId.of(id);
    }
}
