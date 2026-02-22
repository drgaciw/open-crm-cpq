package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Business metrics for evaluating rule variants
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessMetrics {
    
    /**
     * Impact on revenue (percentage change)
     */
    private double revenueImpact;
    
    /**
     * Conversion rate (0.0 - 1.0)
     */
    private double conversionRate;
    
    /**
     * Customer satisfaction score (1-5)
     */
    private double customerSatisfaction;
    
    /**
     * Average order value
     */
    private double averageOrderValue;
    
    /**
     * Discount utilization rate (0.0 - 1.0)
     */
    private double discountUtilization;
}
