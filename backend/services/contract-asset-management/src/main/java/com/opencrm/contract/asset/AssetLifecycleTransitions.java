package com.opencrm.contract.asset;

/**
 * Allowed moves among the four installed-base statuses. Terminal states refuse every move,
 * including a move to themselves. SUSPENDED is not a status in this story.
 */
public final class AssetLifecycleTransitions {

    public boolean allowed(AssetStatus from, AssetStatus to) {
        return false;
    }
}
