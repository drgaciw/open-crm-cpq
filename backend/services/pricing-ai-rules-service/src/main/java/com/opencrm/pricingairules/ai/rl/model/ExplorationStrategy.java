package com.opencrm.pricingairules.ai.rl.model;

/**
 * Defines the strategies for exploring parameter space
 */
public enum ExplorationStrategy {
    /**
     * Randomly explore parameter space with decreasing randomness over time
     */
    EPSILON_GREEDY,
    
    /**
     * Use Upper Confidence Bound algorithm for exploration
     */
    UCB,
    
    /**
     * Use Bayesian optimization for exploration
     */
    BAYESIAN,
    
    /**
     * Use simulated annealing for exploration
     */
    SIMULATED_ANNEALING,
    
    /**
     * Use genetic algorithms for exploration
     */
    GENETIC,
    
    /**
     * Use gradient-based optimization for exploration
     */
    GRADIENT,
    
    /**
     * Use weight-based selection prioritizing high-performing variants
     */
    WEIGHT_BASED,
    
    /**
     * Focus on parameters with the most potential impact
     */
    MOST_IMPACTFUL,
    
    /**
     * Comprehensive testing ensuring all variants get tested
     */
    COMPREHENSIVE
}
