package com.opencrm.quote.approval.testdoubles;

import com.opencrm.quote.quote.QuoteStatus;

/**
 * BFF reject. Suggested production call: {@code com.opencrm.quote.approval.ApprovalService#rejectRequest}.
 * This interface is test-only and is not that FQCN.
 */
public interface QuoteRejectToDraft {

    QuoteStatus reject(long approvalId, String comments);
}
