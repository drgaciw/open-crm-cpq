package com.opencrm.pricing.renewal;

/** Outcome of one renewal unit computation. A non-priced outcome carries no unit price. */
public enum RenewalUpliftResolution {
    PRICED,
    UNRESOLVED_BASIS,
    UNRESOLVED_UPLIFT,
    UNRESOLVED_RENEWAL_MODE,
    PRICE_PROTECTION_CONFLICT
}
