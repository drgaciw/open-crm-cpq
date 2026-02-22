package com.opencrm.pricingairules.ai.sequential;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents the result of analyzing a business requirement for rule creation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleAnalysisResult {
    private List<String> conditions;
    private List<String> actions;
    private List<String> requiredInputs;
    private List<String> expectedOutputs;
    private List<String> dependencies;
    private String businessContext;
}
