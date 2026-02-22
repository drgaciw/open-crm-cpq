package com.opencrm.pricingairules.ai.rl.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Structured output schema for AI-generated rule variants
 * Used with Spring AI's structured output functionality
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariantOutputSchema {
    
    @JsonProperty("variantName")
    private String variantName;
    
    @JsonProperty("variantType")
    private String variantType;
    
    @JsonProperty("ruleContent")
    private String ruleContent;
    
    @JsonProperty("parameterModifications")
    private List<ParameterModification> parameterModifications;
    
    @JsonProperty("optimizationGoals")
    private List<OptimizationTarget> optimizationGoals;
    
    @JsonProperty("reasoning")
    private String reasoning;
    
    /**
     * Represents a single parameter modification in a rule variant
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParameterModification {
        @JsonProperty("parameterName")
        private String parameterName;
        
        @JsonProperty("originalValue")
        private Object originalValue;
        
        @JsonProperty("newValue")
        private Object newValue;
        
        @JsonProperty("expectedImpact")
        private String expectedImpact;
    }
    
    /**
     * Represents an optimization target for the variant
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationTarget {
        @JsonProperty("goalType")
        private String goalType;
        
        @JsonProperty("expectedImprovement")
        private String expectedImprovement;
    }
    
    /**
     * Convert the schema to a Variant object
     */
    public Variant toVariant(Long ruleId, Map<String, Object> originalParameters) {
        Map<String, Object> parameterValues = originalParameters != null 
            ? Map.copyOf(originalParameters) 
            : Map.of();
        
        // Apply parameter modifications
        if (parameterModifications != null) {
            for (ParameterModification mod : parameterModifications) {
                parameterValues.put(mod.getParameterName(), mod.getNewValue());
            }
        }
        
        return Variant.builder()
                .ruleId(ruleId != null ? ruleId.toString() : null)
                .id(java.util.UUID.randomUUID().toString())
                .variantName(variantName)
                .variantType(variantType != null ? getVariantTypeEnum(variantType) : VariantType.PARAMETER_VARIANT)
                .ruleContent(ruleContent)
                .parameterValues(parameterValues)
                .build();
    }
    
    /**
     * Convert string variant type to enum
     */
    private VariantType getVariantTypeEnum(String type) {
        try {
            return VariantType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Default to parameter variant if not recognized
            return VariantType.PARAMETER_VARIANT;
        }
    }
}
