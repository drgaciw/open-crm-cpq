package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.OptimizationMetrics;
import com.opencrm.pricingairules.domain.model.RuleOptimization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface OptimizationMetricsRepository extends JpaRepository<OptimizationMetrics, Long> {
    List<OptimizationMetrics> findByOptimization(RuleOptimization optimization);
    
    @Query("SELECT AVG(m.improvementPct) FROM OptimizationMetrics m WHERE m.optimization = ?1")
    BigDecimal getAverageImprovementForOptimization(RuleOptimization optimization);
    
    @Query("SELECT AVG(m.improvementPct) FROM OptimizationMetrics m JOIN m.optimization o WHERE o.optimizationType = ?1")
    BigDecimal getAverageImprovementForOptimizationType(String optimizationType);
}
