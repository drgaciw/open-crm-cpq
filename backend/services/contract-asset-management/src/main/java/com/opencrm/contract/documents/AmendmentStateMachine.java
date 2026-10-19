package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.SignaturePolicy;

import java.util.EnumSet;
import java.util.Set;

/** First-slice document transitions. Apply outcomes are produced only from EXECUTED. */
public final class AmendmentStateMachine {

    private static final Set<AmendmentDocumentStatus> PRE_EXECUTION = EnumSet.of(
            AmendmentDocumentStatus.DRAFT, AmendmentDocumentStatus.IN_REVIEW, AmendmentDocumentStatus.APPROVED);
    private static final Set<AmendmentDocumentStatus> EXECUTED = EnumSet.of(
            AmendmentDocumentStatus.EXECUTED, AmendmentDocumentStatus.APPLIED,
            AmendmentDocumentStatus.PARTIALLY_APPLIED, AmendmentDocumentStatus.APPLY_BLOCKED);
    private static final Set<AmendmentDocumentStatus> TERMINAL = EnumSet.of(
            AmendmentDocumentStatus.REJECTED, AmendmentDocumentStatus.WITHDRAWN, AmendmentDocumentStatus.VOID);

    private AmendmentStateMachine() {
    }

    public static Transition revise(AmendmentDocumentStatus status) {
        if (PRE_EXECUTION.contains(status)) {
            return Transition.ok(AmendmentDocumentStatus.DRAFT);
        }
        if (EXECUTED.contains(status)) {
            return Transition.reject(status, 409, "IMMUTABLE_EXECUTED");
        }
        return Transition.reject(status, 409, "ILLEGAL_TRANSITION");
    }

    public static Transition submit(AmendmentDocumentStatus status) {
        if (status == AmendmentDocumentStatus.DRAFT) {
            return Transition.ok(AmendmentDocumentStatus.IN_REVIEW);
        }
        return illegal(status);
    }

    public static Transition withdraw(AmendmentDocumentStatus status) {
        if (PRE_EXECUTION.contains(status)) {
            return Transition.ok(AmendmentDocumentStatus.WITHDRAWN);
        }
        if (EXECUTED.contains(status)) {
            return Transition.reject(status, 409, "IMMUTABLE_EXECUTED");
        }
        return illegal(status);
    }

    public static Transition execute(AmendmentDocumentStatus status, SignaturePolicy policy) {
        if (status != AmendmentDocumentStatus.APPROVED) {
            return illegal(status);
        }
        if (policy == SignaturePolicy.WET || policy == SignaturePolicy.E_SIGN) {
            return Transition.reject(status, 422, "SIGNATURE_NOT_SUPPORTED_IN_SLICE");
        }
        return Transition.ok(AmendmentDocumentStatus.EXECUTED);
    }

    public static Transition sendForSignature(AmendmentDocumentStatus status) {
        return Transition.reject(status, 422, "SIGNATURE_NOT_SUPPORTED_IN_SLICE");
    }

    public static Transition approve(AmendmentDocumentStatus status, SignaturePolicy policy) {
        if (status != AmendmentDocumentStatus.IN_REVIEW) {
            return illegal(status);
        }
        if (policy == SignaturePolicy.NOT_REQUIRED || policy == SignaturePolicy.NOTICE_ONLY) {
            return Transition.ok(AmendmentDocumentStatus.EXECUTED);
        }
        return Transition.ok(AmendmentDocumentStatus.APPROVED);
    }

    public static Transition price(AmendmentDocumentStatus status) {
        if (status == AmendmentDocumentStatus.DRAFT) {
            return Transition.ok(status);
        }
        if (EXECUTED.contains(status)) {
            return Transition.reject(status, 409, "IMMUTABLE_EXECUTED");
        }
        return illegal(status);
    }

    /**
     * Apply may move EXECUTED to APPLIED, PARTIALLY_APPLIED, or APPLY_BLOCKED.
     * Every other source state is refused, including VOID, OUT_FOR_SIGNATURE,
     * PARTIALLY_APPLIED and APPLY_BLOCKED.
     */
    public static Transition apply(AmendmentDocumentStatus status, AmendmentDocumentStatus outcome) {
        if (status != AmendmentDocumentStatus.EXECUTED) {
            return illegal(status);
        }
        if (outcome != AmendmentDocumentStatus.APPLIED
                && outcome != AmendmentDocumentStatus.PARTIALLY_APPLIED
                && outcome != AmendmentDocumentStatus.APPLY_BLOCKED) {
            return Transition.reject(status, 409, "ILLEGAL_TRANSITION");
        }
        return Transition.ok(outcome);
    }

    public static Transition rejectReview(AmendmentDocumentStatus status) {
        if (status == AmendmentDocumentStatus.IN_REVIEW) {
            return Transition.ok(AmendmentDocumentStatus.REJECTED);
        }
        return illegal(status);
    }

    /**
     * Expected document row version. A stale token loses with VERSION_CONFLICT even when the row
     * is already executed. A token that matches an executed row is IMMUTABLE_EXECUTED.
     */
    public static Transition onExpectedVersion(int expected, int current, AmendmentDocumentStatus status) {
        if (expected != current) {
            return Transition.reject(status, 409, "VERSION_CONFLICT");
        }
        if (EXECUTED.contains(status)) {
            return Transition.reject(status, 409, "IMMUTABLE_EXECUTED");
        }
        if (TERMINAL.contains(status) || status == AmendmentDocumentStatus.OUT_FOR_SIGNATURE) {
            return Transition.reject(status, 409, "ILLEGAL_TRANSITION");
        }
        return Transition.ok(status);
    }

    private static Transition illegal(AmendmentDocumentStatus status) {
        if (EXECUTED.contains(status)) {
            return Transition.reject(status, 409, "IMMUTABLE_EXECUTED");
        }
        return Transition.reject(status, 409, "ILLEGAL_TRANSITION");
    }
}
