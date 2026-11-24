package com.opencrm.quote.approval.testdoubles;

/** Approval id to quote id. Feature re-points this at the routing approval's quote id. */
public interface QuoteApprovalLink {

    long quoteIdOf(long approvalId);
}
