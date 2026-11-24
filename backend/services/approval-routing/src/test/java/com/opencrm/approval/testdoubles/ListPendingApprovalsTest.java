package com.opencrm.approval.testdoubles;

import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.testdoubles.ApprovalMatrixContract.PendingApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-443 ListPendingApprovals. Red until Feature replaces {@link NotImplementedApprovalMatrix}.
 * The queue for an approver contains that approver's pending rows and no other approver's rows.
 */
@DisplayName("DRG-443 ListPendingApprovals")
class ListPendingApprovalsTest {

    private final PendingApproval dealDesk = new PendingApproval(
            11L, QuoteApprovalTier.DEAL_DESK, "Q-100", 1, ApprovalStatus.PENDING);
    private final PendingApproval cfo = new PendingApproval(
            22L, QuoteApprovalTier.CFO, "Q-200", 1, ApprovalStatus.PENDING);

    private NotImplementedApprovalMatrix service;

    @BeforeEach
    void setUp() {
        service = NotImplementedApprovalMatrix.queueOf(List.of(dealDesk, cfo));
    }

    @Test
    @DisplayName("returns only the queue for that approver")
    void listPendingApprovals_returnsOnlyQueueForThatApprover() {
        assertThat(service.listPendingApprovals(QuoteApprovalTier.DEAL_DESK))
                .containsExactly(dealDesk);
    }

    @Test
    @DisplayName("hides other approvers' items")
    void listPendingApprovals_hidesOtherApproversItems() {
        assertThat(service.listPendingApprovals(QuoteApprovalTier.DEAL_DESK)).doesNotContain(cfo);
        assertThat(service.listPendingApprovals(QuoteApprovalTier.CFO)).doesNotContain(dealDesk);
    }
}
