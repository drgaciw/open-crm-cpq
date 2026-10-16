package com.opencrm.contract.domain.documents;

/** Change-line action. First-slice behavior is Add, Update and Remove; other tokens are stored only. */
public enum ActionCode {
    ADD,
    UPDATE,
    REMOVE,
    NO_CHANGE,
    RENEW,
    UPGRADE,
    DOWNGRADE,
    SWAP,
    CONVERT_METRIC,
    SUSPEND,
    RESUME,
    TERMINATE
}
