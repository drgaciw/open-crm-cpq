package com.opencrm.contract.domain.documents;

/** Revision close status. At most one EXECUTED row per document (partial unique index). */
public enum RevisionCloseStatus {
    OPEN,
    SUPERSEDED,
    EXECUTED,
    REJECTED,
    WITHDRAWN,
    EXPIRED
}
