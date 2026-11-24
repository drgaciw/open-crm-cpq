package com.opencrm.contract.asset;

/** Kind of one scheduled asset lifecycle change. SUSPEND and RESUME are a later story. */
public enum AssetLifecycleChangeKind {
    CREATE,
    AMEND,
    TERMINATE,
    SUPERSEDE
}
