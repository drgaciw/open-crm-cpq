package com.opencrm.contract.domain.documents;

/** How the lifecycle change was started. Stored as amendment_documents.initiation_trigger. */
public enum InitiationTrigger {
    MANUAL,
    LEAD_TIME,
    TERM_END_AUTO,
    THRESHOLD,
    TRUE_UP_CYCLE,
    END_OF_LIFE,
    API
}
