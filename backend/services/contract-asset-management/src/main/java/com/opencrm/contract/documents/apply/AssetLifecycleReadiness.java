package com.opencrm.contract.documents.apply;

import org.springframework.stereotype.Component;

/**
 * S4 cannot safely publish installed-asset changes until effective-date scheduling,
 * durable reconciliation and ordered lifecycle delivery are implemented and verified.
 * There is intentionally no runtime override: releasing the hold requires a code change.
 */
@Component
public final class AssetLifecycleReadiness {
    public boolean isReady() {
        return false;
    }
}
