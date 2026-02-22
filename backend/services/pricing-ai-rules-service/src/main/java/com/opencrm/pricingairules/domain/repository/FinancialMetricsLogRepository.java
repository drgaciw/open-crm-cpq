package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.FinancialMetricsLog;
import com.opencrm.pricingairules.domain.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FinancialMetricsLogRepository extends JpaRepository<FinancialMetricsLog, Long> {
    List<FinancialMetricsLog> findByQuote(Quote quote);
    List<FinancialMetricsLog> findByMetricName(String metricName);
    
    @Query("SELECT DISTINCT m.metricName FROM FinancialMetricsLog m")
    List<String> findDistinctMetricNames();
    
    @Query("SELECT AVG(m.metricValue) FROM FinancialMetricsLog m WHERE m.metricName = ?1")
    BigDecimal getAverageValueForMetric(String metricName);
    
    @Query("SELECT MAX(m.metricValue) FROM FinancialMetricsLog m WHERE m.metricName = ?1")
    BigDecimal getMaxValueForMetric(String metricName);
    
    @Query("SELECT MIN(m.metricValue) FROM FinancialMetricsLog m WHERE m.metricName = ?1")
    BigDecimal getMinValueForMetric(String metricName);
}
