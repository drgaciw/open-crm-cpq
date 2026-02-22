package com.opencrm.pricingairules.service.pricing;

import com.opencrm.pricingairules.domain.model.Customer;
import com.opencrm.pricingairules.domain.model.Quote;
import com.opencrm.pricingairules.domain.model.RuleSet;

import java.util.List;
import java.util.Map;

public interface PricingService {
    
    /**
     * Calculate pricing for a quote
     * 
     * @param quote The quote to calculate pricing for
     * @return The quote with calculated pricing
     */
    Quote calculatePricing(Quote quote);
    
    /**
     * Calculate pricing for a quote using a specific rule set
     * 
     * @param quote The quote to calculate pricing for
     * @param ruleSetName The name of the rule set to use
     * @return The quote with calculated pricing
     */
    Quote calculatePricing(Quote quote, String ruleSetName);
    
    /**
     * Batch calculate pricing for multiple quotes
     * 
     * @param quotes The quotes to calculate pricing for
     * @return The quotes with calculated pricing
     */
    List<Quote> batchCalculatePricing(List<Quote> quotes);
    
    /**
     * Calculate pricing for a quote with additional context
     * 
     * @param quote The quote to calculate pricing for
     * @param additionalContext Additional context for pricing calculations
     * @return The quote with calculated pricing
     */
    Quote calculatePricingWithContext(Quote quote, Map<String, Object> additionalContext);
    
    /**
     * Find the best rule set for a customer
     * 
     * @param customer The customer
     * @return The best matching rule set
     */
    RuleSet findBestRuleSet(Customer customer);
    
    /**
     * Recalculate pricing for existing quotes affected by rule changes
     * 
     * @param ruleSetName The name of the rule set that changed
     * @return The number of quotes recalculated
     */
    int recalculateAffectedQuotes(String ruleSetName);
}
