package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration for a reinforcement learning experiment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentConfig {
    
    // General experiment settings
    private Duration duration;
    private int maxVariants;
    private int minRequiredSamples;
    private double confidenceThreshold;
    private ExperimentType experimentType;
    private int variantCount;
    private int maxParametersInFactorial;
    private double learningRate;
    private double discountFactor;
    
    // Traffic allocation
    private double controlGroupPercentage;
    private boolean dynamicAllocation;
    private boolean consistentAssignment;
    
    // Optimization settings
    private OptimizationGoal primaryGoal;
    private List<WeightedGoal> secondaryGoals;
    private List<OptimizationGoal> optimizationGoals; // Combined list for easy access
    private Map<String, Double> metricWeights;
    
    // Parameter exploration settings
    private ExplorationStrategy explorationStrategy;
    private double explorationRate; // epsilon for epsilon-greedy
    private double explorationDecay; // how quickly to reduce exploration
    private boolean adaptiveExploration;
    
    // Optimization iteration settings
    private int generationsCount;
    private int populationSize;
    private Duration iterationCadence;
    
    // Early stopping criteria
    private boolean enableEarlyStopping;
    private double earlyStoppingThreshold;
    private int earlyStoppingPatience;
    
    // Variant generation settings
    private boolean includeExtremeCases;
    private double parameterMutationRate;
    
    // AI-powered variant generation settings
    private boolean useAiGeneration;
    private int concurrentAiRequests;
    private long aiTimeoutMs;
    private int aiMaxRetries;
    
    // Controls what aspects of the rule are optimized
    private boolean optimizeThresholds;
    private boolean optimizeRanges;
    private boolean optimizeDateValues;
    private boolean optimizeOperators;
    private boolean optimizeCategoricalValues;
    
    // Business rules and constraints
    private double maxDiscountAllowed;
    private double minMarginRequired;
    private List<String> businessRuleConstraints;
    
    /**
     * Create a default configuration
     * 
     * @return a default configuration for experimentation
     */
    public static ExperimentConfig createDefault() {
        ExperimentConfig config = new ExperimentConfig();
        
        // General settings
        config.setDuration(Duration.ofDays(14));
        config.setMaxVariants(5);
        config.setMinRequiredSamples(1000);
        config.setConfidenceThreshold(0.95);
        
        // Traffic allocation
        config.setControlGroupPercentage(0.2);
        config.setDynamicAllocation(true);
        config.setConsistentAssignment(true);
        
        // Optimization settings
        config.setPrimaryGoal(OptimizationGoal.REVENUE);
        
        List<WeightedGoal> secondaryGoals = new ArrayList<>();
        secondaryGoals.add(new WeightedGoal(OptimizationGoal.CONVERSION_RATE, 0.3));
        secondaryGoals.add(new WeightedGoal(OptimizationGoal.EXECUTION_PERFORMANCE, 0.1));
        secondaryGoals.add(new WeightedGoal(OptimizationGoal.CUSTOMER_SATISFACTION, 0.2));
        config.setSecondaryGoals(secondaryGoals);
        
        // Exploration settings
        config.setExplorationStrategy(ExplorationStrategy.EPSILON_GREEDY);
        config.setExplorationRate(0.2);
        config.setExplorationDecay(0.95);
        config.setAdaptiveExploration(true);
        
        // Iteration settings
        config.setGenerationsCount(10);
        config.setPopulationSize(5);
        config.setIterationCadence(Duration.ofDays(1));
        
        // Early stopping
        config.setEnableEarlyStopping(true);
        config.setEarlyStoppingThreshold(0.01);
        config.setEarlyStoppingPatience(3);
        
        // Variant generation
        config.setIncludeExtremeCases(true);
        config.setParameterMutationRate(0.1);
        
        // AI variant generation
        config.setUseAiGeneration(true);
        config.setConcurrentAiRequests(3);
        config.setAiTimeoutMs(10000);
        config.setAiMaxRetries(2);
        
        // Optimization targets
        config.setOptimizeThresholds(true);
        config.setOptimizeRanges(true);
        config.setOptimizeDateValues(true);
        config.setOptimizeOperators(false);
        config.setOptimizeCategoricalValues(true);
        
        // Business constraints
        config.setMaxDiscountAllowed(0.4); // 40% max discount
        config.setMinMarginRequired(0.1); // 10% minimum margin
        
        List<String> constraints = new ArrayList<>();
        constraints.add("Keep total discount budget under $100,000");
        constraints.add("No more than 30% of customers should receive maximum discount");
        config.setBusinessRuleConstraints(constraints);
        
        // Initialize combined optimization goals list
        java.util.List<OptimizationGoal> allGoals = new ArrayList<>();
        allGoals.add(config.getPrimaryGoal());
        for (WeightedGoal goal : config.getSecondaryGoals()) {
            allGoals.add(goal.getGoal());
        }
        config.setOptimizationGoals(allGoals);
        
        return config;
    }
}
