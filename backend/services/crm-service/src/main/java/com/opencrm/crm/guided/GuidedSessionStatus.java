package com.opencrm.crm.guided;

/**
 * Lifecycle of one guided selling session.
 * {@link #DERIVED} means an advisory solution was recorded. It is not a won deal.
 */
public enum GuidedSessionStatus {
    OPEN,
    DERIVED
}
