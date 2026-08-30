package com.opencrm.approval.domain;

public enum ApprovalType {
    DISCOUNT,
    MARGIN,
    TERMS,
    PRICING_EXCEPTION,
    CONTRACT_MODIFICATION,
    /** Quote BFF submit (DEAL_DESK / SALES_VP). */
    QUOTE,
    /** Quote BFF submit at CFO / financial governance. */
    GOVERNANCE
}
