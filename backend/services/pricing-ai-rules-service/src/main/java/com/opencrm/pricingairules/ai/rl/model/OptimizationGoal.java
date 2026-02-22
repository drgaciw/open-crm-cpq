package com.opencrm.pricingairules.ai.rl.model;

/**
 * Defines the possible optimization goals for experiments
 */
public enum OptimizationGoal {
    /**
     * Maximize revenue
     */
    REVENUE("REVENUE"),
    
    /**
     * Maximize profit
     */
    PROFIT("PROFIT"),
    
    /**
     * Maximize conversion rate
     */
    CONVERSION_RATE("CONVERSION_RATE"),
    
    /**
     * Maximize customer lifetime value
     */
    CUSTOMER_LIFETIME_VALUE("CUSTOMER_LIFETIME_VALUE"),
    
    /**
     * Maximize units sold
     */
    UNITS_SOLD("UNITS_SOLD"),
    
    /**
     * Maximize customer satisfaction
     */
    CUSTOMER_SATISFACTION("CUSTOMER_SATISFACTION"),
    
    /**
     * Minimize execution time
     */
    EXECUTION_PERFORMANCE("EXECUTION_PERFORMANCE"),
    
    /**
     * Balance multiple goals
     */
    BALANCED("BALANCED");
    
    private final String type;
    
    OptimizationGoal(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
}

