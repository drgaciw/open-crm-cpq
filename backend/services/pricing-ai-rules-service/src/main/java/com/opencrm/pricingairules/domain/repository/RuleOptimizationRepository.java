package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleOptimization;
import com.opencrm.pricingairules.domain.model.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleOptimizationRepository extends JpaRepository<RuleOptimization, Long> {
    List<RuleOptimization> findByRule(Rule rule);
    List<RuleOptimization> findByRuleSet(RuleSet ruleSet);
    List<RuleOptimization> findByApplied(Boolean applied);
    List<RuleOptimization> findByOptimizationType(String optimizationType);
    List<RuleOptimization> findByRuleAndApplied(Rule rule, Boolean applied);
    long countByApplied(Boolean applied);
    
    @Query("SELECT DISTINCT o.optimizationType FROM RuleOptimization o")
    List<String> findDistinctOptimizationTypes();
}
