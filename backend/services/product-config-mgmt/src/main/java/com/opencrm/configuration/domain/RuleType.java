package com.opencrm.configuration.domain;

/**
 * Types of configuration rules that govern product compatibility.
 */
public enum RuleType {
    /** Two options/products can coexist */
    COMPATIBILITY,
    /** Selecting one option requires another */
    DEPENDENCY,
    /** Selecting one option excludes another */
    EXCLUSION,
    /** Validates quantity constraints */
    QUANTITY_VALIDATION
}
