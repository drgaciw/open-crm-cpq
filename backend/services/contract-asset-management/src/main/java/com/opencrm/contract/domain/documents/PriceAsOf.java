package com.opencrm.contract.domain.documents;

/** Which date pricing used. Default in the field schema is the effective date. */
public enum PriceAsOf {
    ACTION_DATE,
    EFFECTIVE_DATE,
    LINE_START
}
