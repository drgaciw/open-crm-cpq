package com.opencrm.pricingairules.service.drools.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Detailed metrics for individual rule executions collected by DroolsMetricsInterceptor
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroolsRuleExecutionMetrics {
    
    private String ruleName;
    private Double executionTimeMs;
    private Long conditionEvaluations;
    private Long memoryUsageBytes;
    private Long cacheHits;
    private Long cacheMisses;
    private Long factCount;
    private Map<String, Object> patternStats;
    private Map<String, Long> factCounts;
    private Map<String, Double> factAccessTimes;
    private Instant timestamp;
    private String executionContext;
}

