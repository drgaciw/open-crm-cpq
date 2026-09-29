package com.opencrm.quote.approval;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /**
     * Read-only, from approval-routing: a newer discount request for the same quote line replaced this one before
     * anybody decided it. It stays in the history; only the newer request can be decided.
     */
    SUPERSEDED
}
