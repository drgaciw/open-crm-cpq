package com.opencrm.contract.domain.documents;

/** Lifecycle of a canonical contract line (DRG-665). */
public enum ContractLineStatus {
    PENDING,
    ACTIVE,
    SUPERSEDED,
    TERMINATED
}
