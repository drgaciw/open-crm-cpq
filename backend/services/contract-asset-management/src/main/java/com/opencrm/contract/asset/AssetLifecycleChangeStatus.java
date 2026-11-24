package com.opencrm.contract.asset;

/** Processing state of one row in {@code asset_lifecycle_changes}. */
public enum AssetLifecycleChangeStatus {
    PENDING,
    APPLIED,
    SKIPPED,
    FAILED
}
