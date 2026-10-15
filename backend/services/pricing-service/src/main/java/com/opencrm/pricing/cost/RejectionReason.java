package com.opencrm.pricing.cost;

/** Why one row of a cost export was not loaded (F-2: rejected and reported; the rest of the export loads). */
public enum RejectionReason {
    /** Wrong number of fields, or an unterminated quote. */
    MALFORMED_ROW,
    /** Blank, longer than 255 characters, or characters outside {@code A-Z a-z 0-9 . _ : / -}. */
    MALFORMED_PRODUCT_ID,
    /** Not one of the F-2 components. */
    UNKNOWN_COMPONENT,
    /** Not a decimal, or more than four decimal places. */
    MALFORMED_COST,
    NEGATIVE_COST,
    /** Not an ISO 4217 code. */
    INVALID_CURRENCY,
    /** Not an ISO calendar date. */
    MALFORMED_DATE,
    /** Not a positive whole number. */
    MALFORMED_VERSION,
    MISSING_APPROVER,
    MISSING_BILLING_UNIT,
    /** Not in pricing-service's catalog replica ({@code pricing_products}, not deleted). */
    UNKNOWN_PRODUCT,
    /** The catalog states a billing unit ({@code metadata.billingUnit}) and the row names another. */
    BILLING_UNIT_MISMATCH,
    /** The same product, component and effective date appear more than once in the file. */
    DUPLICATE_KEY_IN_FILE,
    /** The components of one product and effective date in the file disagree on billing unit or currency. */
    INCONSISTENT_GROUP,
    /** The row disagrees on billing unit or currency with stored components of the same product and date. */
    CONFLICTS_WITH_STORED,
    /** The same version of this key is already stored with a different cost, unit or currency. */
    VERSION_CONFLICT
}
