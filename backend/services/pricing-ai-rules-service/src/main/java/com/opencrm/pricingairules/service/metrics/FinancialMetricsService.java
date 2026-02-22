package com.opencrm.pricingairules.service.metrics;

import com.opencrm.pricingairules.domain.model.Quote;

import java.math.BigDecimal;
import java.util.Map;

public interface FinancialMetricsService {
    
    /**
     * Calculate financial metrics for a quote
     * 
     * @param quote The quote to calculate metrics for
     * @return The quote with financial metrics updated
     */
    Quote calculateFinancialMetrics(Quote quote);
    
    /**
     * Log a financial metric for a quote
     * 
     * @param quote The quote
     * @param metricName The name of the metric
     * @param metricValue The value of the metric
     */
    void logFinancialMetric(Quote quote, String metricName, BigDecimal metricValue);
    
    /**
     * Get all financial metrics for a quote
     * 
     * @param quote The quote
     * @return A map of metric names to metric values
     */
    Map<String, BigDecimal> getFinancialMetrics(Quote quote);
    
    /**
     * Get aggregated financial metrics across all quotes
     * 
     * @return A map of metric names to aggregated values
     */
    Map<String, Map<String, BigDecimal>> getAggregatedMetrics();
    
    /**
     * Calculate Annual Recurring Revenue (ARR)
     * 
     * @param quote The quote to calculate ARR for
     * @return The calculated ARR
     */
    BigDecimal calculateARR(Quote quote);
    
    /**
     * Calculate Total Contract Value (TCV)
     * 
     * @param quote The quote to calculate TCV for
     * @return The calculated TCV
     */
    BigDecimal calculateTCV(Quote quote);
    
    /**
     * Calculate Annual Contract Value (ACV)
     * 
     * @param quote The quote to calculate ACV for
     * @return The calculated ACV
     */
    BigDecimal calculateACV(Quote quote);
    
    /**
     * Calculate Customer Lifetime Value (CLV)
     * 
     * @param quote The quote to calculate CLV for
     * @return The calculated CLV
     */
    BigDecimal calculateCLV(Quote quote);
}
