package com.opencrm.approval.domain;

/**
 * Outcome of {@code EvaluateQuote} (BC-04-02, DRG-442).
 *
 * <p>{@link #PASS} means every applicable threshold holds. {@link #REQUIRES_APPROVAL} means at least one
 * escalation threshold is breached (discount, term, or a restricted clause) and none is a hard stop.
 * {@link #BLOCKED} means a margin floor is missed, or a threshold cannot be measured. A block dominates
 * an approval breach. Who must approve is the approval matrix (DRG-443) and is not decided here.
 */
public enum PolicyVerdict {
    PASS,
    REQUIRES_APPROVAL,
    BLOCKED
}
