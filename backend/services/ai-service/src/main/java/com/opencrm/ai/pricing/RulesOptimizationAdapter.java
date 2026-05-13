package com.opencrm.ai.pricing;

/**
 * Read-only port from ai-service into the pricing rules optimization boundary.
 * Implementations may request recommendations, but must not publish, activate,
 * or otherwise mutate executable pricing rules.
 */
public interface RulesOptimizationAdapter {

    RulesOptimizationResponse optimizeRule(RulesOptimizationRequest request);

    RulesAnalysisResponse analyzeRule(RulesOptimizationRequest request);
}
