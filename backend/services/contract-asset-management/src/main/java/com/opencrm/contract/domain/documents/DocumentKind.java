package com.opencrm.contract.domain.documents;

/** Document kind derived from intent. RENEWAL is a stored token only; this story has no renewal behavior. */
public enum DocumentKind {
    ORIGINAL,
    AMENDMENT,
    RENEWAL,
    AMENDED_AND_RESTATED,
    TRUE_UP_NOTICE,
    TERMINATION,
    RESTRUCTURE,
    BACKFILLED
}
