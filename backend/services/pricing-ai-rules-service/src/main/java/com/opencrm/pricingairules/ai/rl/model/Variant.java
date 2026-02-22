package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a variant of a rule with specific parameter values
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Variant {
    
    private String id;
    private String ruleId;
    private String ruleName;
    private String variantName;
    private String description;
    private RuleParameters parameters;
    private Map<String, Object> parameterValues;
    private String ruleContent;
    private VariantType variantType;
    
    // Variant performance metrics
    private double revenueImpact;
    private double conversionRateImpact;
    private double averageExecutionTime;
    private int numberOfExecutions;
    private Map<String, Double> metrics;
    
    // Business metrics
    private double averageDiscount;
    private double totalRevenue;
    private double customerSatisfactionScore;
    
    // Overall score combining multiple metrics
    private double overallScore;
    
    // For tracking experiments
    private String experimentId;
    private boolean control;
    private Instant createdAt;
    private Instant lastUpdated;
    private String status; // ACTIVE, PAUSED, ARCHIVED
    
    // History of performance metrics over time
    private List<VariantPerformanceSnapshot> performanceHistory;
    
    /**
     * Generate a new ID for this variant
     * @return this instance for chaining
     */
    public Variant generateId() {
        this.id = UUID.randomUUID().toString();
        return this;
    }
    
    /**
     * Get the variant ID (alias for getId for compatibility)
     * @return the variant ID
     */
    public String getVariantId() {
        return this.id;
    }
    
    /**
     * Create a new variant based on this one, but with different parameters
     * 
     * @param newParameters The new parameters to use
     * @return a new variant with the specified parameters
     */
    public Variant createNewVariant(RuleParameters newParameters) {
        return Variant.builder()
                .ruleId(this.ruleId)
                .ruleName(this.ruleName)
                .variantName(generateVariantName())
                .description("Generated from variant " + this.variantName)
                .parameters(newParameters)
                .experimentId(this.experimentId)
                .control(false)
                .createdAt(Instant.now())
                .status("ACTIVE")
                .build()
                .generateId();
    }
    
    /**
     * Generate a variant name based on the rule name
     * 
     * @return a new variant name
     */
    private String generateVariantName() {
        return this.ruleName + "-Variant-" + UUID.randomUUID().toString().substring(0, 8);
    }
    
    /**
     * Update the performance metrics for this variant
     * 
     * @param revenueImpact The revenue impact percentage
     * @param conversionRateImpact The conversion rate impact percentage
     * @param executionTime The average execution time in ms
     * @param executions The number of executions
     * @param avgDiscount The average discount percentage
     * @param revenue The total revenue generated
     * @param satisfaction The customer satisfaction score
     */
    public void updateMetrics(double revenueImpact, double conversionRateImpact, 
                             double executionTime, int executions, 
                             double avgDiscount, double revenue, double satisfaction) {
        this.revenueImpact = revenueImpact;
        this.conversionRateImpact = conversionRateImpact;
        this.averageExecutionTime = executionTime;
        this.numberOfExecutions = executions;
        this.averageDiscount = avgDiscount;
        this.totalRevenue = revenue;
        this.customerSatisfactionScore = satisfaction;
        this.lastUpdated = Instant.now();
        
        // Calculate overall score based on weighted metrics
        calculateOverallScore();
        
        // Add a snapshot to performance history
        addPerformanceSnapshot();
    }
    
    /**
     * Calculate the overall score for this variant
     * This is a weighted score based on multiple metrics
     */
    private void calculateOverallScore() {
        // Define weights for each metric (these would typically be configurable)
        double revenueWeight = 0.4;
        double conversionWeight = 0.3;
        double performanceWeight = 0.1;
        double satisfactionWeight = 0.2;
        
        // Normalize execution time (lower is better, so we invert it)
        double normalizedExecutionTime = 1.0;
        if (averageExecutionTime > 0) {
            // Assuming 100ms is a good baseline execution time
            normalizedExecutionTime = Math.min(1.0, 100.0 / averageExecutionTime);
        }
        
        // Calculate weighted score
        this.overallScore = 
            (revenueImpact * revenueWeight) +
            (conversionRateImpact * conversionWeight) +
            (normalizedExecutionTime * performanceWeight) +
            (customerSatisfactionScore * satisfactionWeight);
    }
    
    /**
     * Add a performance snapshot to the history
     */
    private void addPerformanceSnapshot() {
        if (performanceHistory == null) {
            performanceHistory = new java.util.ArrayList<>();
        }
        
        VariantPerformanceSnapshot snapshot = new VariantPerformanceSnapshot(
            Instant.now(),
            revenueImpact,
            conversionRateImpact,
            averageExecutionTime,
            numberOfExecutions,
            averageDiscount,
            totalRevenue,
            customerSatisfactionScore,
            overallScore
        );
        
        performanceHistory.add(snapshot);
    }
}
