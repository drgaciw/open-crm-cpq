package com.opencrm.contract.domain.documents;

/** Where a renewal policy body is keyed. Clause text stays on the document; rules stay outside this store. */
public enum RenewalPolicyScopeKind {
    ACCOUNT,
    AGREEMENT,
    LINE
}
