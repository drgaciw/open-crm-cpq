package com.opencrm.contract.asset;

/** Fact kinds the history projection accepts. Only CREATED is a published contract today. */
public enum AssetHistoryKind {
    CREATED,
    AMENDED,
    SUPERSEDED,
    TERMINATED
}
