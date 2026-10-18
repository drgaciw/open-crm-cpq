package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.SignaturePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** DoD v0.3 item 18. Document transitions for the first slice, including the two OQ6 outcomes. */
class AmendmentStateMachineTest {

    @ParameterizedTest
    @EnumSource(value = AmendmentDocumentStatus.class, names = {"DRAFT", "IN_REVIEW", "APPROVED"})
    void reviseBeforeExecutionReturnsToDraft(AmendmentDocumentStatus status) {
        Transition transition = AmendmentStateMachine.revise(status);
        assertThat(transition.allowed()).isTrue();
        assertThat(transition.status()).isEqualTo(AmendmentDocumentStatus.DRAFT);
        assertThat(transition.errorCode()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = AmendmentDocumentStatus.class, names = {
            "EXECUTED", "APPLIED", "PARTIALLY_APPLIED", "APPLY_BLOCKED"})
    void reviseAfterExecutionIsImmutable(AmendmentDocumentStatus status) {
        Transition transition = AmendmentStateMachine.revise(status);
        assertThat(transition.allowed()).isFalse();
        assertThat(transition.status()).isEqualTo(status);
        assertThat(transition.httpStatus()).isEqualTo(409);
        assertThat(transition.errorCode()).isEqualTo("IMMUTABLE_EXECUTED");
    }

    @ParameterizedTest
    @EnumSource(value = AmendmentDocumentStatus.class, names = {"REJECTED", "WITHDRAWN", "VOID", "OUT_FOR_SIGNATURE"})
    void reviseOfTerminalOrUnsupportedSignatureStateIsIllegal(AmendmentDocumentStatus status) {
        Transition transition = AmendmentStateMachine.revise(status);
        assertThat(transition.allowed()).isFalse();
        assertThat(transition.status()).isEqualTo(status);
        assertThat(transition.httpStatus()).isEqualTo(409);
        assertThat(transition.errorCode()).isEqualTo("ILLEGAL_TRANSITION");
    }

    @Test
    void submitMovesDraftToReviewAndRejectsEveryOtherState() {
        assertThat(AmendmentStateMachine.submit(AmendmentDocumentStatus.DRAFT).status())
                .isEqualTo(AmendmentDocumentStatus.IN_REVIEW);
        Transition rejected = AmendmentStateMachine.submit(AmendmentDocumentStatus.APPROVED);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.errorCode()).isEqualTo("ILLEGAL_TRANSITION");
        assertThat(rejected.status()).isEqualTo(AmendmentDocumentStatus.APPROVED);
    }

    @ParameterizedTest
    @EnumSource(value = AmendmentDocumentStatus.class, names = {"DRAFT", "IN_REVIEW", "APPROVED"})
    void withdrawBeforeExecutionIsTerminalAndConsumesNoFurtherTransition(AmendmentDocumentStatus status) {
        Transition withdrawn = AmendmentStateMachine.withdraw(status);
        assertThat(withdrawn.allowed()).isTrue();
        assertThat(withdrawn.status()).isEqualTo(AmendmentDocumentStatus.WITHDRAWN);
        Transition again = AmendmentStateMachine.withdraw(AmendmentDocumentStatus.WITHDRAWN);
        assertThat(again.errorCode()).isEqualTo("ILLEGAL_TRANSITION");
    }

    @Test
    void withdrawOfExecutedDocumentIsImmutable() {
        Transition transition = AmendmentStateMachine.withdraw(AmendmentDocumentStatus.EXECUTED);
        assertThat(transition.errorCode()).isEqualTo("IMMUTABLE_EXECUTED");
        assertThat(transition.status()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
    }

    @ParameterizedTest
    @EnumSource(value = SignaturePolicy.class, names = {"CLICK_THROUGH", "NOT_REQUIRED", "NOTICE_ONLY"})
    void approvedDocumentWithSliceSignaturePolicyCanExecute(SignaturePolicy policy) {
        Transition transition = AmendmentStateMachine.execute(AmendmentDocumentStatus.APPROVED, policy);
        assertThat(transition.allowed()).isTrue();
        assertThat(transition.status()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
    }

    @ParameterizedTest
    @EnumSource(value = SignaturePolicy.class, names = {"WET", "E_SIGN"})
    void wetAndEsignExecutionStayApprovedWithSliceRejection(SignaturePolicy policy) {
        Transition transition = AmendmentStateMachine.execute(AmendmentDocumentStatus.APPROVED, policy);
        assertThat(transition.allowed()).isFalse();
        assertThat(transition.status()).isEqualTo(AmendmentDocumentStatus.APPROVED);
        assertThat(transition.httpStatus()).isEqualTo(422);
        assertThat(transition.errorCode()).isEqualTo("SIGNATURE_NOT_SUPPORTED_IN_SLICE");
    }

    @Test
    void sendForSignatureIsUnsupportedInTheSlice() {
        Transition transition = AmendmentStateMachine.sendForSignature(AmendmentDocumentStatus.APPROVED);
        assertThat(transition.allowed()).isFalse();
        assertThat(transition.status()).isEqualTo(AmendmentDocumentStatus.APPROVED);
        assertThat(transition.httpStatus()).isEqualTo(422);
        assertThat(transition.errorCode()).isEqualTo("SIGNATURE_NOT_SUPPORTED_IN_SLICE");
    }

    @Test
    void clickThroughApprovalStopsAtApprovedWhileNoticePoliciesAutoExecute() {
        assertThat(AmendmentStateMachine.approve(AmendmentDocumentStatus.IN_REVIEW, SignaturePolicy.CLICK_THROUGH).status())
                .isEqualTo(AmendmentDocumentStatus.APPROVED);
        assertThat(AmendmentStateMachine.approve(AmendmentDocumentStatus.IN_REVIEW, SignaturePolicy.WET).status())
                .isEqualTo(AmendmentDocumentStatus.APPROVED);
        assertThat(AmendmentStateMachine.approve(AmendmentDocumentStatus.IN_REVIEW, SignaturePolicy.NOT_REQUIRED).status())
                .isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(AmendmentStateMachine.approve(AmendmentDocumentStatus.IN_REVIEW, SignaturePolicy.NOTICE_ONLY).status())
                .isEqualTo(AmendmentDocumentStatus.EXECUTED);
    }

    @Test
    void pricingIsAcceptedOnlyOnAnOpenDraft() {
        assertThat(AmendmentStateMachine.price(AmendmentDocumentStatus.DRAFT).allowed()).isTrue();
        assertThat(AmendmentStateMachine.price(AmendmentDocumentStatus.IN_REVIEW).errorCode()).isEqualTo("ILLEGAL_TRANSITION");
        assertThat(AmendmentStateMachine.price(AmendmentDocumentStatus.EXECUTED).errorCode()).isEqualTo("IMMUTABLE_EXECUTED");
    }

    @Test
    void staleVersionLosesWithConflictAndAMatchingExecutedVersionIsImmutable() {
        Transition stale = AmendmentStateMachine.onExpectedVersion(1, 2, AmendmentDocumentStatus.EXECUTED);
        assertThat(stale.allowed()).isFalse();
        assertThat(stale.httpStatus()).isEqualTo(409);
        assertThat(stale.errorCode()).isEqualTo("VERSION_CONFLICT");

        Transition observed = AmendmentStateMachine.onExpectedVersion(2, 2, AmendmentDocumentStatus.EXECUTED);
        assertThat(observed.errorCode()).isEqualTo("IMMUTABLE_EXECUTED");

        Transition winner = AmendmentStateMachine.onExpectedVersion(4, 4, AmendmentDocumentStatus.APPROVED);
        assertThat(winner.allowed()).isTrue();
        assertThat(winner.errorCode()).isNull();
    }

    @Test
    void everyOtherTransitionFromEveryStateIsRefused() {
        for (AmendmentDocumentStatus status : AmendmentDocumentStatus.values()) {
            boolean preExecution = status == AmendmentDocumentStatus.DRAFT
                    || status == AmendmentDocumentStatus.IN_REVIEW
                    || status == AmendmentDocumentStatus.APPROVED;
            assertAllowed(AmendmentStateMachine.revise(status), preExecution);
            assertAllowed(AmendmentStateMachine.submit(status), status == AmendmentDocumentStatus.DRAFT);
            assertAllowed(AmendmentStateMachine.withdraw(status), preExecution);
            assertAllowed(AmendmentStateMachine.price(status), status == AmendmentDocumentStatus.DRAFT);
            assertAllowed(AmendmentStateMachine.rejectReview(status), status == AmendmentDocumentStatus.IN_REVIEW);
            assertThat(AmendmentStateMachine.sendForSignature(status).allowed()).isFalse();
            for (SignaturePolicy policy : SignaturePolicy.values()) {
                boolean executeAllowed = status == AmendmentDocumentStatus.APPROVED
                        && policy != SignaturePolicy.WET && policy != SignaturePolicy.E_SIGN;
                assertAllowed(AmendmentStateMachine.execute(status, policy), executeAllowed);
                assertAllowed(AmendmentStateMachine.approve(status, policy), status == AmendmentDocumentStatus.IN_REVIEW);
            }
        }
    }

    private static void assertAllowed(Transition transition, boolean allowed) {
        assertThat(transition.allowed()).isEqualTo(allowed);
        if (!allowed) {
            assertThat(transition.errorCode()).isNotBlank();
            assertThat(transition.httpStatus()).isIn(409, 422);
        }
    }

    @Test
    void applyFromExecutedReachesEachApplyOutcomeAndEveryOtherStateIsRefused() {
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.EXECUTED, AmendmentDocumentStatus.APPLIED).allowed())
                .isTrue();
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.EXECUTED, AmendmentDocumentStatus.PARTIALLY_APPLIED).status())
                .isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.EXECUTED, AmendmentDocumentStatus.APPLY_BLOCKED).status())
                .isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        for (AmendmentDocumentStatus status : AmendmentDocumentStatus.values()) {
            for (AmendmentDocumentStatus outcome : AmendmentDocumentStatus.values()) {
                boolean allowed = status == AmendmentDocumentStatus.EXECUTED
                        && (outcome == AmendmentDocumentStatus.APPLIED
                        || outcome == AmendmentDocumentStatus.PARTIALLY_APPLIED
                        || outcome == AmendmentDocumentStatus.APPLY_BLOCKED);
                assertAllowed(AmendmentStateMachine.apply(status, outcome), allowed);
            }
        }
    }

    @Test
    void rejectReviewIsTerminal() {
        assertThat(AmendmentStateMachine.rejectReview(AmendmentDocumentStatus.IN_REVIEW).status())
                .isEqualTo(AmendmentDocumentStatus.REJECTED);
        assertThat(AmendmentStateMachine.revise(AmendmentDocumentStatus.REJECTED).errorCode())
                .isEqualTo("ILLEGAL_TRANSITION");
    }
}
