package com.opencrm.pricingairules.ai.orchestration;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of evaluating a rule set through the AI orchestration process
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleEvaluationResult {
    private Long ruleSetId;
    private Map<String, Object> performanceData;
    private String performanceAnalysis;
    private List<String> recommendations;
    private boolean success;
    private String errorMessage;
}
