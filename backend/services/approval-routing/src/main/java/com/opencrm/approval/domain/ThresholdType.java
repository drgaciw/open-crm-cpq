package com.opencrm.approval.domain;

/**
 * The commercial dimension a {@link PolicyThreshold} governs (BC-04-01, DRG-441).
 */
public enum ThresholdType {
    /** Maximum discount percentage allowed without escalation. */
    DISCOUNT_PERCENT,
    /** Minimum gross margin percentage a deal must retain. */
    MARGIN_FLOOR,
    /** Maximum contract term, in months, allowed without escalation. */
    TERM_MONTHS,
    /** A specific non-standard legal/commercial clause that is restricted or requires escalation. */
    NON_STANDARD_CLAUSE
}
