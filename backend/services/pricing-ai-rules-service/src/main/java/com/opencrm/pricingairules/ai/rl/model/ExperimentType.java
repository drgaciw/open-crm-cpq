package com.opencrm.pricingairules.ai.rl.model;

/**
 * Defines the types of experiments that can be conducted
 */
public enum ExperimentType {
    /**
     * Experiment that modifies one parameter at a time
     */
    SINGLE_PARAMETER,
    
    /**
     * Experiment that modifies multiple parameters at once
     */
    MULTI_PARAMETER,
    
    /**
     * Experiment that tests all combinations of parameter values
     */
    FULL_FACTORIAL
}
