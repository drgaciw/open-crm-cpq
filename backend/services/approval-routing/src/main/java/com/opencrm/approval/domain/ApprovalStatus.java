package com.opencrm.approval.domain;

public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED,
    /**
     * Created by a matrix submission for a later serial step. It is not in the approver inbox until the prior step
     * completes. Advancing it is DRG-444; {@code approve}/{@code reject} still require {@link #PENDING}.
     */
    WAITING,
    /**
     * Terminal. A newer discount request for the same quote line replaced this one before anyone decided it, so nobody
     * can approve figures the quote no longer carries (owner decision 9). The row stays as history and names its
     * replacement in {@code supersededBy}.
     */
    SUPERSEDED
}
