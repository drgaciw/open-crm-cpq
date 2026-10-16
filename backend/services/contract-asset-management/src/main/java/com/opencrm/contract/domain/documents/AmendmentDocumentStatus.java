package com.opencrm.contract.domain.documents;

/** Single document status. Apply outcomes share this column; there is no apply_status. */
public enum AmendmentDocumentStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    OUT_FOR_SIGNATURE,
    REJECTED,
    EXECUTED,
    WITHDRAWN,
    APPLIED,
    PARTIALLY_APPLIED,
    APPLY_BLOCKED,
    VOID
}
