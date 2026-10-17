package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;

/** One state-machine answer. A refusal keeps {@code status} unchanged. */
public record Transition(boolean allowed, AmendmentDocumentStatus status, int httpStatus, String errorCode) {

    public static Transition ok(AmendmentDocumentStatus status) {
        return new Transition(true, status, 200, null);
    }

    public static Transition reject(AmendmentDocumentStatus status, int httpStatus, String errorCode) {
        return new Transition(false, status, httpStatus, errorCode);
    }
}
