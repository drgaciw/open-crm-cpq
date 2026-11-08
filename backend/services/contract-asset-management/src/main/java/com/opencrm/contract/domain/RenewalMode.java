package com.opencrm.contract.domain;

/**
 * How a subscription advances at the committed period end (DRG-664).
 * Persisted on {@code subscriptions.renewal_mode}. Null on legacy rows is fail-safe MANUAL.
 */
public enum RenewalMode {
    AUTO_ROLL,
    MANUAL
}
