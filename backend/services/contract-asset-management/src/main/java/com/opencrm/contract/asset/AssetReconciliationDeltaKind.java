package com.opencrm.contract.asset;

/**
 * Unexplained differences between acceptances, asset rows, and published asset events.
 * DRG-902 AC3. Identities are only those already stored; nothing here is synthesized.
 */
public enum AssetReconciliationDeltaKind {
    /** An applied acceptance line has no assets row. */
    ACCEPTANCE_WITHOUT_ASSET,
    /** A live asset has no AssetCreated outbox row. */
    ASSET_WITHOUT_CREATED_EVENT,
    /** A published asset lifecycle event points at no assets row. */
    EVENT_WITHOUT_ASSET,
    /** The assets row and the as-of installed-base projection disagree. */
    STATE_MISMATCH,
    /** No contract_lines match, or the line id ends with {@code :successor}. */
    UNVERIFIED_LINE_IDENTITY
}
