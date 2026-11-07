package com.opencrm.crm.duplicate;

/**
 * Why a candidate was suggested. Suggestions are not merges.
 */
public enum DuplicateMatchReason {
    DOMAIN,
    EMAIL,
    NAME_SAME_ORG
}
