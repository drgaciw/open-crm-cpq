package com.opencrm.pricingairules.ai.rl.model;

/**
 * Enum representing the type of a rule variant
 */
public enum VariantType {
    /**
     * The original rule, unmodified
     */
    BASELINE,
    
    /**
     * Variant with a single parameter modified
     */
    PARAMETER_VARIANT,
    
    /**
     * Variant with multiple parameters modified
     */
    MULTI_PARAMETER_VARIANT,
    
    /**
     * Variant from a full factorial design
     */
    FACTORIAL_VARIANT,
    
    /**
     * Variant created by AI
     */
    AI_GENERATED
}
