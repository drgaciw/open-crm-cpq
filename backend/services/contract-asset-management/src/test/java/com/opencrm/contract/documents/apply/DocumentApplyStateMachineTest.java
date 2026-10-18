package com.opencrm.contract.documents.apply;

import com.opencrm.contract.documents.AmendmentStateMachine;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-S4-8. DoD v0.3 Core 3 and v0.3 add 3. */
@Tag("unit")
class DocumentApplyStateMachineTest {

    @Test
    void everyNonExecutedStateIsRefusedIncludingVoidSignatureAndApplyOutcomes() {
        for (AmendmentDocumentStatus status : AmendmentDocumentStatus.values()) {
            if (status == AmendmentDocumentStatus.EXECUTED) {
                continue;
            }
            var transition = AmendmentStateMachine.apply(status, AmendmentDocumentStatus.APPLIED);
            assertThat(transition.allowed()).isFalse();
            assertThat(transition.status()).isEqualTo(status);
            assertThat(transition.httpStatus()).isIn(409, 422);
            assertThat(transition.errorCode()).isNotBlank();
        }
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.VOID, AmendmentDocumentStatus.APPLIED).errorCode())
                .isEqualTo("ILLEGAL_TRANSITION");
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.OUT_FOR_SIGNATURE, AmendmentDocumentStatus.APPLIED).errorCode())
                .isEqualTo("ILLEGAL_TRANSITION");
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.PARTIALLY_APPLIED, AmendmentDocumentStatus.APPLIED).errorCode())
                .isEqualTo("IMMUTABLE_EXECUTED");
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.APPLY_BLOCKED, AmendmentDocumentStatus.APPLIED).errorCode())
                .isEqualTo("IMMUTABLE_EXECUTED");
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.EXECUTED, AmendmentDocumentStatus.DRAFT).allowed())
                .isFalse();
    }
}
