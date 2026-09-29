package com.opencrm.approval.domain;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    /**
     * Terminal. A newer discount request for the same quote line replaced this one before anyone decided it, so nobody
     * can approve figures the quote no longer carries (owner decision 9). The row stays as history and names its
     * replacement in {@code supersededBy}.
     */
    SUPERSEDED
}
