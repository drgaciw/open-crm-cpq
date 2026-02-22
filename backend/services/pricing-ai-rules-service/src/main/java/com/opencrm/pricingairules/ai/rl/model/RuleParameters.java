package com.opencrm.pricingairules.ai.rl.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents the tunable parameters of a rule that can be optimized through
 * reinforcement learning
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleParameters {

    private String id;
    private String ruleId;
    private String ruleName;
    private String version;

    // Numerical parameters with their ranges
    // Numerical parameters with their ranges
    private Map<String, ParameterValue<Double>> numericalParameters;

    // Categorical parameters with their possible values
    private Map<String, ParameterValue<String>> categoricalParameters;

    // Boolean parameters
    private Map<String, ParameterValue<Boolean>> booleanParameters;

    // For tracking changes and history
    private Instant createdAt;
    private Instant lastModified;
    private String lastModifiedBy;

    // Parent parameters (for tracking evolutionary changes)
    private String parentId;

    /**
     * Generate a new ID for these parameters
     * 
     * @return this instance for chaining
     */
    public RuleParameters generateId() {
        this.id = UUID.randomUUID().toString();
        return this;
    }

    /**
     * Create a new version with the same parameters
     * 
     * @return a new copy with a new ID
     */
    public RuleParameters createNewVersion() {
        RuleParameters newParams = new RuleParameters();
        newParams.setRuleId(this.ruleId);
        newParams.setRuleName(this.ruleName);
        newParams.setVersion(this.version);
        newParams.setNumericalParameters(Map.copyOf(this.numericalParameters));
        newParams.setCategoricalParameters(Map.copyOf(this.categoricalParameters));
        newParams.setBooleanParameters(Map.copyOf(this.booleanParameters));
        newParams.setCreatedAt(Instant.now());
        newParams.setParentId(this.id);
        return newParams.generateId();
    }

    /**
     * Check if these parameters are valid
     * 
     * @return true if all parameters are within their constraints
     */
    public boolean isValid() {
        // Check numerical parameters are within their range
        boolean numericValid = numericalParameters.values().stream()
                .allMatch(value -> {
                    if (value instanceof NumericalParameterValue) {
                        NumericalParameterValue numValue = (NumericalParameterValue) value;
                        return numValue.getValue() >= numValue.getMinValue() &&
                                numValue.getValue() <= numValue.getMaxValue();
                    }
                    return true;
                });

        // Check categorical parameters have valid values from their options
        boolean categoricalValid = categoricalParameters.values().stream()
                .allMatch(value -> {
                    if (value instanceof CategoricalParameterValue) {
                        CategoricalParameterValue catValue = (CategoricalParameterValue) value;
                        return catValue.getPossibleValues().contains(catValue.getValue());
                    }
                    return true;
                });

        return numericValid && categoricalValid;
    }

    /**
     * Get all parameters as a list
     * 
     * @return List of all parameter values
     */
    public List<ParameterValue<?>> getParameterList() {
        List<ParameterValue<?>> allParams = new ArrayList<>();

        if (numericalParameters != null) {
            allParams.addAll(numericalParameters.values());
        }
        if (categoricalParameters != null) {
            allParams.addAll(categoricalParameters.values());
        }
        if (booleanParameters != null) {
            allParams.addAll(booleanParameters.values());
        }

        return allParams;
    }
}
