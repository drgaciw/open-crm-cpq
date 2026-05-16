package com.opencrm.quote.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Quote Approval Ownership")
class QuoteApprovalOwnershipTest {

    @Test
    @DisplayName("quote-service owns manual quote workflow approvals only")
    void quoteServiceOwnsManualWorkflowApprovals() {
        assertEquals(
                "quote-service",
                QuoteApprovalOwnership.OWNER,
                "Quote service must own explicit submit/approve flows tied to quote status"
        );
        assertTrue(
                QuoteApprovalOwnership.ROUTED_VIA_APPROVAL_ROUTING.contains("DISCOUNT"),
                "Discount threshold approvals are created by approval-routing from pricing events"
        );
    }
}
