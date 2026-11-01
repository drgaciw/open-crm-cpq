package com.opencrm.crm.merge;

import org.springframework.stereotype.Component;

/** No production override: tenant/account authorization and downstream reconciliation are not yet implemented. */
@Component
public final class AccountMergeReadiness {
    public boolean isReady() {
        return false;
    }
}
