package com.opencrm.pricing.renewal;

/**
 * Mode names from the uplift ADR. This slice prices {@link #UPLIFT_PCT} only.
 * The other modes stay unpriced; their catalog is DRG-549.
 */
public enum RenewalUpliftMode {
    SAME,
    LIST,
    UPLIFT_PCT,
    UPLIFT_AMT,
    OVERRIDE,
    RENEGOTIATE
}
