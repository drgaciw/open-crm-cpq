package com.opencrm.pricingairules.service.optimization;

import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleOptimization;
import com.opencrm.pricingairules.domain.model.RuleSet;

import java.util.List;

public interface RuleOptimizationService {
    
    /**
     * Optimize a rule using AI
     * 
     * @param rule The rule to optimize
     * @param ruleContent The rule content to optimize
     * @return The optimized rule content
     */
    String optimizeRule(Rule rule, String ruleContent);
    
    /**
     * Optimize a rule set using AI
     * 
     * @param ruleSet The rule set to optimize
     * @return A list of optimized rules
     */
    List<RuleOptimization> optimizeRuleSet(RuleSet ruleSet);
    
    /**
     * Apply a rule optimization
     * 
     * @param optimization The optimization to apply
     * @return True if the optimization was applied successfully
     */
    boolean applyOptimization(RuleOptimization optimization);
    
    /**
     * Test a rule optimization to measure its performance impact
     * 
     * @param optimization The optimization to test
     * @return The performance improvement percentage
     */
    double testOptimization(RuleOptimization optimization);
    
    /**
     * Find optimization opportunities for a rule
     * 
     * @param rule The rule to analyze
     * @return A list of potential optimizations
     */
    List<RuleOptimization> findOptimizationOpportunities(Rule rule);
}
