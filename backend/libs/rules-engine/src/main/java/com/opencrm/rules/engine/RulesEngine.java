package com.opencrm.rules.engine;

import java.util.List;

/**
 * Core interface for rules engine implementations.
 */
public interface RulesEngine {

    /**
     * Execute rules using a predefined rule set.
     */
    RuleExecutionResult execute(RuleExecutionContext context);

    /**
     * Execute rules with dynamically provided rule definitions.
     */
    RuleExecutionResult execute(RuleExecutionContext context, List<RuleDefinition> rules);

    /**
     * Invalidate cached rule sets for a given rule set ID.
     */
    void invalidateCache(String ruleSetId);

    /**
     * Check if the rules engine is operational.
     */
    boolean isHealthy();

    /**
     * Get the type of rules engine.
     */
    RulesEngineType getEngineType();
}
