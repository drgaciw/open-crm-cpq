package com.opencrm.contract.asset;

/**
 * Applies one due lifecycle change. Implementations arrive in later stories.
 * This story registers none, so a due row fails closed per line when the hold is lifted.
 */
public interface AssetLifecycleChangeApplier {

    AssetLifecycleChangeKind kind();

    void apply(AssetLifecycleChange change);
}
