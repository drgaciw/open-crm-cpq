package com.opencrm.approval.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApprovalRequest: superseding (owner decision 9)")
class ApprovalRequestSupersedeTest {

    private static ApprovalRequest request(ApprovalStatus status) {
        return ApprovalRequest.builder().quoteId("Q-1").approvalType(ApprovalType.DISCOUNT).status(status)
                .requesterId("service:approval-routing").build();
    }

    @Test
    @DisplayName("a PENDING request becomes SUPERSEDED, naming the request that replaced it")
    void pendingCanBeSuperseded() {
        ApprovalRequest request = request(ApprovalStatus.PENDING);

        request.supersede(9L);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.SUPERSEDED);
        assertThat(request.getSupersededBy()).isEqualTo(9L);
        assertThat(request.getSupersededAt()).isNotNull();
    }

    @Test
    @DisplayName("a decided or cancelled request is history and is not superseded")
    void onlyPendingCanBeSuperseded() {
        for (ApprovalStatus status : new ApprovalStatus[] {ApprovalStatus.APPROVED, ApprovalStatus.REJECTED,
                ApprovalStatus.CANCELLED, ApprovalStatus.SUPERSEDED}) {
            ApprovalRequest request = request(status);

            assertThatThrownBy(() -> request.supersede(9L)).isInstanceOf(IllegalStateException.class);
            assertThat(request.getStatus()).isEqualTo(status);
            assertThat(request.getSupersededBy()).isNull();
        }
    }

    @Test
    @DisplayName("the replacement must be named")
    void replacementIsRequired() {
        ApprovalRequest request = request(ApprovalStatus.PENDING);

        assertThatThrownBy(() -> request.supersede(null)).isInstanceOf(NullPointerException.class);
        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    @DisplayName("a superseded request can be neither approved, rejected nor cancelled")
    void supersededIsTerminal() {
        ApprovalRequest request = request(ApprovalStatus.PENDING);
        request.supersede(9L);

        assertThatThrownBy(() -> request.approve("oid-1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.reject("oid-1", "no")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(request::cancel).isInstanceOf(IllegalStateException.class);
    }
}