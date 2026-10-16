package com.opencrm.contract.domain.documents;

/** Lifecycle intent copied onto the document. Research labels such as Renew are stored as RENEW. */
public enum AmendmentIntent {
    RENEW,
    AMEND,
    CANCEL,
    RESTRUCTURE,
    TRANSFER,
    SUSPEND,
    RESUME,
    TRUE_UP,
    CONVERT
}
