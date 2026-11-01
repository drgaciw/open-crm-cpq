package com.opencrm.quote.lifecycle;

/** Commercial lifecycle statuses. Phase A moves DRAFT to PRICED to IN_APPROVAL. */
public enum LifecycleChangeStatus {
    DRAFT,
    PRICED,
    IN_APPROVAL,
    APPROVED,
    PRESENTED,
    ACCEPTED,
    REJECTED,
    EXPIRED,
    SUPERSEDED,
    ORDERED,
    APPLIED,
    REVERTED,
    WITHDRAWN
}
