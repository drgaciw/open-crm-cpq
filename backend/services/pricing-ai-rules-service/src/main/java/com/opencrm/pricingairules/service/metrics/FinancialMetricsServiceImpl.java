package com.opencrm.pricingairules.service.metrics;

import com.opencrm.pricingairules.domain.model.FinancialMetricsLog;
import com.opencrm.pricingairules.domain.model.Quote;
import com.opencrm.pricingairules.domain.repository.FinancialMetricsLogRepository;
import com.opencrm.pricingairules.domain.repository.QuoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinancialMetricsServiceImpl implements FinancialMetricsService {

    private final FinancialMetricsLogRepository metricsLogRepository;
    private final QuoteRepository quoteRepository;

    @Override
    @Transactional
    public Quote calculateFinancialMetrics(Quote quote) {
        // Calculate all financial metrics
        BigDecimal arr = calculateARR(quote);
        BigDecimal tcv = calculateTCV(quote);
        BigDecimal acv = calculateACV(quote);
        BigDecimal clv = calculateCLV(quote);
        
        // Update quote with calculated metrics
        quote.setARR(arr);
        quote.setTCV(tcv);
        quote.setACV(acv);
        quote.setCLV(clv);
        
        // Log all metrics
        logFinancialMetric(quote, "ARR", arr);
        logFinancialMetric(quote, "TCV", tcv);
        logFinancialMetric(quote, "ACV", acv);
        logFinancialMetric(quote, "CLV", clv);
        
        // Save the updated quote
        return quoteRepository.save(quote);
    }

    @Override
    public void logFinancialMetric(Quote quote, String metricName, BigDecimal metricValue) {
        log.debug("Logging financial metric: {} = {} for quote {}", metricName, metricValue, quote.getExternalId());
        
        FinancialMetricsLog metricsLog = FinancialMetricsLog.builder()
                .quote(quote)
                .metricName(metricName)
                .metricValue(metricValue)
                .build();
        
        metricsLogRepository.save(metricsLog);
    }

    @Override
    public Map<String, BigDecimal> getFinancialMetrics(Quote quote) {
        List<FinancialMetricsLog> metrics = metricsLogRepository.findByQuote(quote);
        
        return metrics.stream()
                .collect(Collectors.toMap(
                        FinancialMetricsLog::getMetricName,
                        FinancialMetricsLog::getMetricValue,
                        (v1, v2) -> v2 // Keep the latest value in case of duplicates
                ));
    }

    @Override
    public Map<String, Map<String, BigDecimal>> getAggregatedMetrics() {
        Map<String, Map<String, BigDecimal>> result = new HashMap<>();
        
        // Get average values for all metrics
        Map<String, BigDecimal> averages = new HashMap<>();
        List<String> metricNames = metricsLogRepository.findDistinctMetricNames();
        
        for (String metricName : metricNames) {
            BigDecimal avgValue = metricsLogRepository.getAverageValueForMetric(metricName);
            if (avgValue != null) {
                averages.put(metricName, avgValue);
            }
        }
        result.put("AVERAGE", averages);
        
        // Get min values for all metrics
        Map<String, BigDecimal> mins = new HashMap<>();
        for (String metricName : metricNames) {
            BigDecimal minValue = metricsLogRepository.getMinValueForMetric(metricName);
            if (minValue != null) {
                mins.put(metricName, minValue);
            }
        }
        result.put("MIN", mins);
        
        // Get max values for all metrics
        Map<String, BigDecimal> maxes = new HashMap<>();
        for (String metricName : metricNames) {
            BigDecimal maxValue = metricsLogRepository.getMaxValueForMetric(metricName);
            if (maxValue != null) {
                maxes.put(metricName, maxValue);
            }
        }
        result.put("MAX", maxes);
        
        return result;
    }

    @Override
    public BigDecimal calculateARR(Quote quote) {
        // ARR = Monthly Price × 12
        if (quote.getMonthlyPrice() == null) {
            return BigDecimal.ZERO;
        }
        
        return quote.getMonthlyPrice().multiply(BigDecimal.valueOf(12))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateTCV(Quote quote) {
        // TCV = Monthly Price × Duration in Months
        if (quote.getMonthlyPrice() == null || quote.getDurationInMonths() == null) {
            return BigDecimal.ZERO;
        }
        
        return quote.getMonthlyPrice().multiply(BigDecimal.valueOf(quote.getDurationInMonths()))
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateACV(Quote quote) {
        // ACV = (Monthly Price × Duration) / 12
        if (quote.getMonthlyPrice() == null || quote.getDurationInMonths() == null) {
            return BigDecimal.ZERO;
        }
        
        return quote.getMonthlyPrice()
                .multiply(BigDecimal.valueOf(quote.getDurationInMonths()))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateCLV(Quote quote) {
        // CLV = Monthly Price × Expected Duration
        if (quote.getMonthlyPrice() == null || quote.getExpectedDuration() == null) {
            return BigDecimal.ZERO;
        }
        
        return quote.getMonthlyPrice().multiply(BigDecimal.valueOf(quote.getExpectedDuration()))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
