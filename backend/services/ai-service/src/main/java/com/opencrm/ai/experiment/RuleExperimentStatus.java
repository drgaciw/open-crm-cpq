package com.opencrm.ai.experiment;

/**
 * Lifecycle of a rule optimization experiment.
 * Apply creates a DRAFT rule only — never activates a rule set.
 */
public enum RuleExperimentStatus {
    CREATED,
    RUNNING,
    COMPLETED,
    FAILED,
    APPLIED
}
