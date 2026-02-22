package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * Metrics collected for a rule variant during testing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantMetrics {
    
    /**
     * Average execution time in milliseconds
     */
    private double averageExecutionTimeMs;
    
    /**
     * Memory usage in kilobytes
     */
    private double memoryUsageKb;
    
    /**
     * Rule complexity score
     */
    private double ruleComplexity;
    
    /**
     * Business metrics (revenue, conversion, etc.)
     */
    private BusinessMetrics businessMetrics;
    
    /**
     * When these metrics were collected
     */
    @Builder.Default
    private ZonedDateTime collectedAt = ZonedDateTime.now();
    
    /**
     * Number of samples used to calculate these metrics
     */
    private int sampleCount;
}
