package com.opencrm.ai.experiment;

/**
 * Metrics recorded for a running experiment variant (business + execution).
 */
public record UpdateVariantMetricsRequest(
        Double averageExecutionMs,
        Integer sampleCount,
        Double revenueImpact,
        Double conversionRateImpact,
        Double averageDiscount,
        Double totalRevenue,
        Double customerSatisfaction,
        Double overallScore,
        String metricsJson
) {
}
