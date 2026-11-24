package com.opencrm.approval.domain;

/**
 * How one breached threshold contributes to {@link PolicyVerdict}.
 */
public enum BreachEffect {
    /** The quote can proceed only with an approval. The matrix that names the approver is DRG-443. */
    REQUIRES_APPROVAL,
    /** The quote cannot be submitted. A floor was missed, or the value the threshold needs was not measured. */
    BLOCKED
}
