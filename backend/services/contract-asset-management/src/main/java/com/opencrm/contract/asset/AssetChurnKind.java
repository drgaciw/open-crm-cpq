package com.opencrm.contract.asset;

/** Why an asset is in the churn report. A supersede is never a churn kind. */
public enum AssetChurnKind {
    TERMINATED,
    DOWNSIZED
}
