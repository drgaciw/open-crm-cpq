package com.opencrm.pricing.renewal;

/**
 * Mode names from the uplift ADR. This slice prices {@link #UPLIFT_PCT} and {@link #SAME}.
 * {@link #LIST}, {@link #UPLIFT_AMT}, {@link #OVERRIDE}, and {@link #RENEGOTIATE} stay unpriced.
 */
public enum RenewalUpliftMode {
    SAME,
    LIST,
    UPLIFT_PCT,
    UPLIFT_AMT,
    OVERRIDE,
    RENEGOTIATE
}
