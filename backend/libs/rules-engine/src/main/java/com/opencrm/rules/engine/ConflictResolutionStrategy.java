package com.opencrm.rules.engine;

/**
 * Strategy for resolving conflicts when multiple rules match.
 */
public enum ConflictResolutionStrategy {
    HIGHEST_PRIORITY,
    HIGHEST_IMPACT,
    LEXICAL
}
