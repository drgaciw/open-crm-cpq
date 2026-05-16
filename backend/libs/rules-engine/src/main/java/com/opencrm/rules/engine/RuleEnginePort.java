package com.opencrm.rules.engine;

/**
 * Application-facing port for deterministic hybrid rule execution.
 */
public interface RuleEnginePort {

    RuleExecutionResult execute(RuleExecutionRequest request);
}
