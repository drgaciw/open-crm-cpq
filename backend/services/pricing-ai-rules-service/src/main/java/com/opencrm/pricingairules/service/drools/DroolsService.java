package com.opencrm.pricingairules.service.drools;

import com.opencrm.pricingairules.domain.model.Quote;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleSet;
import com.opencrm.pricingairules.service.drools.dto.*;

import java.util.List;
import java.util.Map;

/**
 * Primary interface for interacting with the Drools rule engine
 * Implements all functionality described in the technical specification
 */
public interface DroolsService {
    
    // Rule Deployment
    
    /**
     * Deploy a new rule to the rule engine
     * 
     * @param ruleContent The content of the rule to deploy
     * @return Deployment result with status and metadata
     */
    RuleDeploymentResult deployRules(String ruleContent);
    
    /**
     * Update an existing rule in the rule engine
     * 
     * @param ruleContent The updated content of the rule
     * @param version The version of the rule to update
     * @return Deployment result with status and metadata
     */
    RuleDeploymentResult updateRules(String ruleContent, String version);
    
    /**
     * Undeploy a rule from the rule engine
     * 
     * @param version The version of the rule to undeploy
     */
    void undeployRules(String version);
    
    // Rule Validation
    
    /**
     * Validate rule content without deploying it
     * 
     * @param ruleContent The content of the rule to validate
     * @return Validation result with errors and warnings
     */
    RuleValidationResult validateRules(String ruleContent);
    
    /**
     * Validate an entire rule set
     * 
     * @param ruleSetId The ID of the rule set to validate
     * @return Validation result with errors and warnings
     */
    RuleValidationResult validateRuleSet(String ruleSetId);
    
    // Rule Execution
    
    /**
     * Evaluate pricing rules for a quote
     * 
     * @param quote The quote to be evaluated
     * @param ruleSetName The name of the rule set to use
     * @return The evaluated quote with updated pricing
     */
    Quote evaluateQuote(Quote quote, String ruleSetName);
    
    /**
     * Evaluate pricing rules for a quote with additional facts
     * 
     * @param quote The quote to be evaluated
     * @param ruleSetName The name of the rule set to use
     * @param additionalFacts Additional facts to be inserted into the session
     * @return The evaluated quote with updated pricing
     */
    Quote evaluateQuote(Quote quote, String ruleSetName, Map<String, Object> additionalFacts);
    
    /**
     * Execute rules with generic facts
     * 
     * @param <T> The type of the result
     * @param ruleSetId The ID of the rule set to execute
     * @param facts The facts to insert into the session
     * @return The result of the rule execution
     */
    <T> T executeRules(String ruleSetId, Map<String, Object> facts);
    
    /**
     * Execute rules in batch mode
     * 
     * @param <T> The type of the results
     * @param ruleSetId The ID of the rule set to execute
     * @param facts The list of facts to process
     * @return The list of results
     */
    <T> List<T> executeBatchRules(String ruleSetId, List<Map<String, Object>> facts);
    
    /**
     * Batch evaluate pricing rules for multiple quotes
     * 
     * @param quotes The quotes to be evaluated
     * @param ruleSetName The name of the rule set to use
     * @return The evaluated quotes with updated pricing
     */
    Map<String, Quote> evaluateQuotes(Map<String, Quote> quotes, String ruleSetName);
    
    // Rule Management
    
    /**
     * Get metadata for a rule set
     * 
     * @param ruleSetId The ID of the rule set
     * @return Metadata about the rule set
     */
    RuleSetMetadata getRuleSetMetadata(String ruleSetId);
    
    /**
     * List all available rule sets
     * 
     * @return List of rule set metadata
     */
    List<RuleSetMetadata> listRuleSets();
    
    /**
     * Reload a rule set from storage
     * 
     * @param ruleSetId The ID of the rule set to reload
     */
    void reloadRuleSet(String ruleSetId);
    
    /**
     * Compile a rule and verify it's valid
     * 
     * @param rule The rule to compile
     * @return True if the rule was compiled successfully
     */
    boolean compileRule(Rule rule);
    
    /**
     * Compile a rule set and verify it's valid
     * 
     * @param ruleSet The rule set to compile
     * @return True if the rule set was compiled successfully
     */
    boolean compileRuleSet(RuleSet ruleSet);
    
    /**
     * Clear the rule cache for a specific rule set
     * 
     * @param ruleSetName The name of the rule set to clear from cache
     */
    void clearRuleCache(String ruleSetName);
    
    // Rule Monitoring
    
    /**
     * Get execution metrics for a rule set
     * 
     * @param ruleSetId The ID of the rule set
     * @return Execution metrics
     */
    RuleExecutionMetrics getRuleExecutionMetrics(String ruleSetId);
    
    /**
     * Get cache metrics for a rule set
     * 
     * @param ruleSetId The ID of the rule set
     * @return Cache metrics
     */
    RuleCacheMetrics getRuleCacheMetrics(String ruleSetId);
    
    /**
     * Validate a single rule content
     * 
     * @param ruleContent The content of the rule to validate
     * @return Validation result with errors and warnings
     */
    RuleValidationResult validateRule(String ruleContent);
    
    /**
     * Deploy a rule temporarily for testing
     * 
     * @param ruleId The ID of the rule to deploy
     * @param ruleContent The content of the rule to deploy
     * @return Deployment result
     */
    RuleDeploymentResult deployTemporaryRule(String ruleId, String ruleContent);
    
    /**
     * Undeploy a temporary rule
     * 
     * @param ruleId The ID of the rule to undeploy
     */
    void undeployTemporaryRule(String ruleId);
}

