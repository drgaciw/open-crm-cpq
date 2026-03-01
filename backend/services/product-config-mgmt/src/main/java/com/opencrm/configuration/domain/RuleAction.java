package com.opencrm.configuration.domain;

/**
 * Action to take when a configuration rule is triggered.
 */
public enum RuleAction {
    /** Automatically add the target option */
    AUTO_ADD,
    /** Automatically remove the target option */
    AUTO_REMOVE,
    /** Warn the user but allow the configuration */
    WARN,
    /** Block the configuration and return an error */
    BLOCK,
    /** Enforce a specific quantity */
    ENFORCE_QUANTITY
}
