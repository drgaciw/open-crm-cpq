package com.opencrm.crm.fact;

/**
 * Behaviour band from the computed score. Sensitive fields are stored one band lower.
 * A null band is only used when contradicting evidence is kept below the storage threshold.
 */
public enum FactBand {
    VERIFIED,
    PROBABLE,
    POSSIBLE
}
