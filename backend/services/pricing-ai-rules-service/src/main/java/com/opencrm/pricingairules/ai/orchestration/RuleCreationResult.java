package com.opencrm.pricingairules.ai.orchestration;

import com.opencrm.pricingairules.ai.puppeteer.TestResult;
import com.opencrm.pricingairules.ai.sequential.RuleAnalysisResult;
import com.opencrm.pricingairules.ai.sequential.TestCase;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Result of creating a rule through the AI orchestration process.
 * Encapsulates all stages from initial requirement to final optimized rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RuleCreationResult {

    private static final double PRODUCTION_READY_PASS_RATE = 1.0;
    private static final double ACCEPTABLE_PASS_RATE = 0.8;

    private String businessRequirement;
    private RuleAnalysisResult analysis;
    private List<String> documentationExamples;
    private String draftRule;
    private String enhancedRule;
    private String finalRule;
    private List<TestCase> testCases;
    private List<TestResult> testResults;
    private List<TestResult> finalTestResults;
    private boolean success;
    private boolean optimized;
    private String errorMessage;

    @Builder.Default
    private Instant createdAt = Instant.now();
    private Instant completedAt;

    public boolean isValid() {
        return businessRequirement != null && !businessRequirement.isBlank() &&
                finalRule != null && !finalRule.isBlank();
    }

    public double calculatePassRate() {
        if (finalTestResults == null || finalTestResults.isEmpty()) {
            return 0.0;
        }
        long passedCount = finalTestResults.stream().filter(TestResult::isSuccess).count();
        return (double) passedCount / finalTestResults.size();
    }

    public boolean isReadyForProduction() {
        return success && optimized && !hasError() && calculatePassRate() >= PRODUCTION_READY_PASS_RATE;
    }

    public boolean meetsMinimumQuality() {
        return success && !hasError() && calculatePassRate() >= ACCEPTABLE_PASS_RATE;
    }

    /**
     * Returns the best available rule in the creation pipeline.
     * Priority: finalRule > enhancedRule > draftRule
     */
    public Optional<String> getBestAvailableRule() {
        if (finalRule != null && !finalRule.isBlank()) {
            return Optional.of(finalRule);
        }
        if (enhancedRule != null && !enhancedRule.isBlank()) {
            return Optional.of(enhancedRule);
        }
        if (draftRule != null && !draftRule.isBlank()) {
            return Optional.of(draftRule);
        }
        return Optional.empty();
    }

    public int getTotalTestCount() {
        return testCases != null ? testCases.size() : 0;
    }

    public int getPassedTestCount() {
        if (finalTestResults == null) {
            return 0;
        }
        return (int) finalTestResults.stream().filter(TestResult::isSuccess).count();
    }

    public int getFailedTestCount() {
        return getTotalTestCount() - getPassedTestCount();
    }

    public List<TestResult> getFailedTests() {
        if (finalTestResults == null) {
            return List.of();
        }
        return finalTestResults.stream()
                .filter(result -> !result.isSuccess())
                .toList();
    }

    public void addDocumentationExample(String example) {
        if (documentationExamples == null) {
            documentationExamples = new ArrayList<>();
        }
        documentationExamples.add(example);
    }

    public void markComplete() {
        this.completedAt = Instant.now();
    }

    public static RuleCreationResult failure(String errorMessage) {
        return RuleCreationResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();
    }

    public static RuleCreationResult inProgress(String businessRequirement) {
        return RuleCreationResult.builder()
                .businessRequirement(businessRequirement)
                .success(false)
                .optimized(false)
                .createdAt(Instant.now())
                .build();
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public boolean isComplete() {
        return success && finalRule != null && !finalRule.isBlank();
    }

    public String getSummary() {
        if (hasError()) {
            return "Failed: " + errorMessage;
        }
        if (!isComplete()) {
            return "In progress - " + getCurrentStage();
        }
        return String.format("Complete - Pass rate: %.0f%% (%d/%d tests), Optimized: %s",
                calculatePassRate() * 100,
                getPassedTestCount(),
                getTotalTestCount(),
                optimized ? "Yes" : "No");
    }

    private String getCurrentStage() {
        if (finalRule != null)
            return "Final rule generated";
        if (enhancedRule != null)
            return "Rule enhanced";
        if (draftRule != null)
            return "Draft created";
        if (analysis != null)
            return "Analysis complete";
        return "Starting";
    }
}

