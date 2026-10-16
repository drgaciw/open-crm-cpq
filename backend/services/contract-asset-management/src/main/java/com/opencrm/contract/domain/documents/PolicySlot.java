package com.opencrm.contract.domain.documents;

/** Policy extension slot. Renewal and price-lock slots are stored tokens only in S1. */
public enum PolicySlot {
    AMENDMENT,
    RENEWAL,
    PRICE_LOCK,
    TRUE_UP,
    MAINTENANCE,
    CONVERSION
}
