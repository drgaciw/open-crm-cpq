package com.opencrm.pricingairules.ai.orchestration;

import com.opencrm.pricingairules.ai.puppeteer.TestResult;
import com.opencrm.pricingairules.ai.sequential.TestCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of modifying a rule through the AI orchestration process
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleModificationResult {
    private Long ruleId;
    private String modificationRequirement;
    private String originalRule;
    private String impactAnalysis;
    private String modifiedRule;
    private List<TestCase> testCases;
    private List<TestResult> testResults;
    private boolean success;
    private String errorMessage;
}
