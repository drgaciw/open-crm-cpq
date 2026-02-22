package com.opencrm.pricingairules.service.drools.dto;

import com.opencrm.pricingairules.domain.model.Quote;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleSet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO for rule execution metrics
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RuleExecutionMetrics {
    private Long id;
    private String ruleName;
    private Rule rule;
    private RuleSet ruleSet;
    private Quote quote;
    private Long executionTimeMs;
    @Builder.Default
    private Long totalExecutionTime = 0L;
    private Long lastExecutionTime;
    @Builder.Default
    private Integer executionCount = 0;
    private Boolean success;
    private String errorMessage;
    private ZonedDateTime executionDate;
    private String executionContext;
    
    // Metrics tracking
    @Builder.Default
    private Map<String, Object> patternStats = new HashMap<>();
    @Builder.Default
    private Map<String, Long> factCounts = new HashMap<>();
    @Builder.Default
    private Map<String, Double> factAccessTimes = new HashMap<>();
    private Long cacheHits;
    private Long cacheMisses;
    private Long factCount;
    private Long conditionEvaluations;
    private Long memoryUsageBytes;
    @Builder.Default
    private Instant timestamp = Instant.now();
    
    // Helper methods for metrics tracking
    public void incrementExecutionCount() {
        if (executionCount == null) {
            executionCount = 0;
        }
        executionCount++;
    }
    
    public void addExecutionTime(long timeMs) {
        if (totalExecutionTime == null) {
            totalExecutionTime = 0L;
        }
        totalExecutionTime += timeMs;
        lastExecutionTime = timeMs;
    }
    
    public double getAverageExecutionTime() {
        if (executionCount == null || executionCount == 0) {
            return 0.0;
        }
        return (double) totalExecutionTime / executionCount;
    }
    
    public double getCacheHitRate() {
        if ((cacheHits == null || cacheHits == 0) && (cacheMisses == null || cacheMisses == 0)) {
            return 0.0;
        }
        long total = (cacheHits != null ? cacheHits : 0) + (cacheMisses != null ? cacheMisses : 0);
        return total > 0 ? (double) (cacheHits != null ? cacheHits : 0) / total * 100.0 : 0.0;
    }
}
