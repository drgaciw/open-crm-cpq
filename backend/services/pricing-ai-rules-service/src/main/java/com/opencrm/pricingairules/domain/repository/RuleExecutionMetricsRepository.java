package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleExecutionMetrics;
import com.opencrm.pricingairules.domain.model.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;

@Repository
public interface RuleExecutionMetricsRepository extends JpaRepository<RuleExecutionMetrics, Long> {
    List<RuleExecutionMetrics> findByRule(Rule rule);
    List<RuleExecutionMetrics> findByRuleSet(RuleSet ruleSet);
    
    @Query("SELECT AVG(m.executionTimeMs) FROM RuleExecutionMetrics m WHERE m.rule = ?1 AND m.success = true")
    Double getAverageExecutionTimeForRule(Rule rule);
    
    @Query("SELECT AVG(m.executionTimeMs) FROM RuleExecutionMetrics m WHERE m.ruleSet = ?1 AND m.success = true")
    Double getAverageExecutionTimeForRuleSet(RuleSet ruleSet);
    
    List<RuleExecutionMetrics> findByExecutionDateBetween(ZonedDateTime start, ZonedDateTime end);
}
