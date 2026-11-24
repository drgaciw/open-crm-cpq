package com.opencrm.approval.spec;

import com.opencrm.approval.testdoubles.ApprovalDecision;
import com.opencrm.approval.testdoubles.ApprovalDecisions;
import com.opencrm.approval.testdoubles.ApprovalEventLog;
import com.opencrm.approval.testdoubles.ApprovalRequestStore;
import com.opencrm.approval.testdoubles.ApproverIdentity;
import com.opencrm.approval.testdoubles.InMemoryApprovalEventLog;
import com.opencrm.approval.testdoubles.NotImplementedApprovalDecisions;
import com.opencrm.approval.testdoubles.PendingApproval;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DRG-444 red spec for Approve and Reject with comments, and for {@code ApprovalGranted} / {@code ApprovalRejected}.
 *
 * <p>Drives {@link NotImplementedApprovalDecisions}. It does not call
 * {@code com.opencrm.approval.service.ApprovalService}, whose {@code approve(Long)} takes no comments and whose
 * reject publishes {@code QuoteApprovalRejected}. Feature replaces the double with the real decision service
 * and keeps these assertions. Not the D1-3 cancel cascade.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-444 ApprovalDecisionService")
class ApprovalDecisionServiceTest {

    private static final long APPROVAL_ID = 7L;
    private static final String APPROVER_ID = "oid-approver";

    @Mock
    private ApprovalRequestStore store;

    @Mock
    private ApproverIdentity approver;

    private ApprovalEventLog events;
    private ApprovalDecisions decisions;

    @BeforeEach
    void setUp() {
        events = new InMemoryApprovalEventLog();
        decisions = new NotImplementedApprovalDecisions(store, approver, events);
        when(store.find(APPROVAL_ID)).thenReturn(Optional.of(
                new PendingApproval(APPROVAL_ID, "Q-42", "PENDING", "deal-desk")));
        when(approver.currentApproverId()).thenReturn(APPROVER_ID);
    }

    @Test
    @DisplayName("approve records the comment and the approver")
    void approveRecordsCommentsAndApprover() {
        ApprovalDecision decision = decisions.approve(APPROVAL_ID, "Ship it");

        assertThat(decision)
                .extracting(ApprovalDecision::status, ApprovalDecision::comments, ApprovalDecision::approverId)
                .containsExactly("APPROVED", "Ship it", APPROVER_ID);
    }

    @Test
    @DisplayName("reject records the comment and the approver")
    void rejectRecordsCommentsAndApprover() {
        ApprovalDecision decision = decisions.reject(APPROVAL_ID, "Discount too high");

        assertThat(decision)
                .extracting(ApprovalDecision::status, ApprovalDecision::comments, ApprovalDecision::approverId)
                .containsExactly("REJECTED", "Discount too high", APPROVER_ID);
    }

    @Test
    @DisplayName("approve emits ApprovalGranted")
    void approveEmitsApprovalGranted() {
        decisions.approve(APPROVAL_ID, "Ship it");

        assertThat(events.emitted()).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("ApprovalGranted");
            assertThat(event.approvalId()).isEqualTo(APPROVAL_ID);
            assertThat(event.quoteId()).isEqualTo("Q-42");
            assertThat(event.actorId()).isEqualTo(APPROVER_ID);
            assertThat(event.comments()).isEqualTo("Ship it");
        });
    }

    @Test
    @DisplayName("reject emits ApprovalRejected")
    void rejectEmitsApprovalRejected() {
        decisions.reject(APPROVAL_ID, "Discount too high");

        assertThat(events.emitted()).singleElement().satisfies(event -> {
            assertThat(event.name()).isEqualTo("ApprovalRejected");
            assertThat(event.approvalId()).isEqualTo(APPROVAL_ID);
            assertThat(event.quoteId()).isEqualTo("Q-42");
            assertThat(event.actorId()).isEqualTo(APPROVER_ID);
            assertThat(event.comments()).isEqualTo("Discount too high");
        });
    }
}
