package com.opencrm.approval.domain;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * D1-1 living spec: Deal Desk {@code DealStatus} transitions vs routing {@link ApprovalRequest}.
 *
 * <p>Matrix IDs T1–T4 from {@code docs/plans/deal-desk-capability-matrix-d1.md}.
 * Source Deal {@code SUBMITTED} ≈ {@link ApprovalStatus#PENDING}; {@code APPROVED}/{@code REJECTED}
 * map 1:1; source {@code CANCELLED} ≈ {@link ApprovalStatus#CANCELLED}.
 */
@Tag("unit")
@DisplayName("D1-1 Deal Desk → ApprovalRequest transition living spec")
class DealDeskD1TransitionLivingSpecTest {

    private static ApprovalRequest pending() {
        ApprovalRequest request = ApprovalRequest.builder()
                .quoteId("42")
                .approvalType(ApprovalType.QUOTE)
                .status(ApprovalStatus.PENDING)
                .requesterId("rep-1")
                .approvalTier("DEAL_DESK")
                .build();
        request.setId(1L);
        return request;
    }

    @Nested
    @DisplayName("T1/T2/T3 happy paths (source SUBMITTED → APPROVED/REJECTED)")
    class HappyPaths {

        @Test
        @DisplayName("T2: PENDING approve → APPROVED")
        void pendingApprove() {
            ApprovalRequest request = pending();
            request.approve("mgr-1");
            assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
            assertThat(request.getApproverId()).isEqualTo("mgr-1");
            assertThat(request.getApprovedAt()).isNotNull();
        }

        @Test
        @DisplayName("T3: PENDING reject → REJECTED")
        void pendingReject() {
            ApprovalRequest request = pending();
            request.reject("mgr-1", "too steep");
            assertThat(request.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
            assertThat(request.getRejectionReason()).isEqualTo("too steep");
        }

        @Test
        @DisplayName("T4: PENDING cancel → CANCELLED")
        void pendingCancel() {
            ApprovalRequest request = pending();
            request.cancel();
            assertThat(request.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("D1-2: invalid approve/reject/cancel throw INVALID_STATE_TRANSITION")
    class InvalidTransitions {

        @Test
        @DisplayName("T2: cannot approve APPROVED (double-approve)")
        void cannotApproveApproved() {
            ApprovalRequest request = pending();
            request.approve("mgr-1");
            assertInvalidState(() -> request.approve("mgr-2"), "approve", ApprovalStatus.APPROVED);
        }

        @Test
        @DisplayName("T3: cannot reject APPROVED")
        void cannotRejectApproved() {
            ApprovalRequest request = pending();
            request.approve("mgr-1");
            assertInvalidState(() -> request.reject("mgr-2", "no"), "reject", ApprovalStatus.APPROVED);
        }

        @Test
        @DisplayName("T2/T3: cannot decide REJECTED")
        void cannotDecideRejected() {
            ApprovalRequest request = pending();
            request.reject("mgr-1", "no");
            assertInvalidState(() -> request.approve("mgr-2"), "approve", ApprovalStatus.REJECTED);
            assertInvalidState(() -> request.reject("mgr-2", "again"), "reject", ApprovalStatus.REJECTED);
        }

        @Test
        @DisplayName("T4: cannot cancel already APPROVED or REJECTED")
        void cannotCancelProcessed() {
            ApprovalRequest approved = pending();
            approved.approve("mgr-1");
            assertInvalidState(approved::cancel, "cancel", ApprovalStatus.APPROVED);

            ApprovalRequest rejected = pending();
            rejected.reject("mgr-1", "no");
            assertInvalidState(rejected::cancel, "cancel", ApprovalStatus.REJECTED);
        }
    }

    private static void assertInvalidState(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            String action, ApprovalStatus current) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo("INVALID_STATE_TRANSITION");
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("action=" + action);
                    assertThat(ex.getMessage()).contains("currentStatus=" + current);
                    assertThat(ex.getMessage()).contains("targetStatus=");
                });
    }
}
