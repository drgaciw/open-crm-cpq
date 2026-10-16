package com.opencrm.contract.domain.documents;

/** Signature policy pinned on the document. E-sign execution is out of slice (DRG-457). */
public enum SignaturePolicy {
    WET,
    E_SIGN,
    CLICK_THROUGH,
    NOTICE_ONLY,
    NOT_REQUIRED
}
