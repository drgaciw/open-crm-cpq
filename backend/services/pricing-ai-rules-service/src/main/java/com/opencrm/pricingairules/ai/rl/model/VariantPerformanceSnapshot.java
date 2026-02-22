package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a point-in-time snapshot of a variant's performance metrics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariantPerformanceSnapshot {
    
    // Timestamp when this snapshot was taken
    private Instant timestamp;
    
    // Performance metrics
    private double revenueImpact;
    private double conversionRateImpact;
    private double averageExecutionTime;
    private int numberOfExecutions;
    
    // Business metrics
    private double averageDiscount;
    private double totalRevenue;
    private double customerSatisfactionScore;
    
    // Overall score
    private double overallScore;
}
