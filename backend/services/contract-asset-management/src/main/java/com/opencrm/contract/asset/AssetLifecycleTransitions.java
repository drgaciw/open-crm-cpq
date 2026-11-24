package com.opencrm.contract.asset;

/**
 * Allowed moves among the four installed-base statuses. Terminal states refuse every move,
 * including a move to themselves. SUSPENDED is not a status in this story.
 */
public final class AssetLifecycleTransitions {

    public boolean allowed(AssetStatus from, AssetStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return switch (from) {
            case ACTIVE, AMENDED -> to == AssetStatus.AMENDED
                    || to == AssetStatus.TERMINATED
                    || to == AssetStatus.SUPERSEDED;
            case SUPERSEDED, TERMINATED -> false;
        };
    }
}
