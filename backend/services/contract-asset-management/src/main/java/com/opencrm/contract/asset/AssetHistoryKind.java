package com.opencrm.contract.asset;

/** Fact kinds the history projection accepts. Each kind is a published asset event. */
public enum AssetHistoryKind {
    CREATED,
    AMENDED,
    SUPERSEDED,
    TERMINATED
}
