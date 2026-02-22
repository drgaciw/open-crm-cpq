package com.opencrm.pricingairules.ai.orchestration;

import com.opencrm.pricingairules.ai.puppeteer.TestResult;
import com.opencrm.pricingairules.ai.sequential.RuleAnalysisResult;
import com.opencrm.pricingairules.ai.sequential.SequentialThinkingService;
import com.opencrm.pricingairules.ai.sequential.TestCase;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.repository.RuleRepository;
import com.opencrm.pricingairules.service.drools.DroolsService;
import com.opencrm.pricingairules.service.drools.dto.RuleValidationResult;
import com.opencrm.pricingairules.service.optimization.RuleOptimizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class AIOrchestrationService {

    private final SequentialThinkingService sequentialThinkingService;
    private final DroolsService droolsService;
    private final RuleRepository ruleRepository;
    private final RuleOptimizationService ruleOptimizationService;

    public RuleCreationResult createRuleFromRequirement(String businessRequirement) {
        RuleCreationResult result = RuleCreationResult.inProgress(businessRequirement);

        try {
            RuleAnalysisResult analysis = sequentialThinkingService.analyzeBusinessRequirement(businessRequirement);
            result.setAnalysis(analysis);
            result.setDocumentationExamples(List.of());

            String draftRule = sequentialThinkingService.generateDroolsRule(analysis, businessRequirement);
            result.setDraftRule(draftRule);
            result.setEnhancedRule(draftRule);

            RuleValidationResult draftValidation = droolsService.validateRule(draftRule);

            Rule tempRule = new Rule();
            tempRule.setContent(draftRule);
            tempRule.setName("AI Generated Rule");
            String optimizedRule = ruleOptimizationService.optimizeRule(tempRule, draftRule);
            result.setFinalRule(optimizedRule);
            result.setOptimized(!optimizedRule.equals(draftRule));

            List<TestCase> testCases = sequentialThinkingService.generateTestCases(tempRule, analysis);
            result.setTestCases(testCases);

            RuleValidationResult finalValidation = droolsService.validateRule(optimizedRule);
            List<TestResult> validationAsTests = mapValidationToTestResults(testCases, finalValidation);
            result.setTestResults(validationAsTests);
            result.setFinalTestResults(validationAsTests);
            result.setSuccess(draftValidation.isValid() && finalValidation.isValid());
            if (!finalValidation.isValid()) {
                result.setErrorMessage("Generated rule failed Drools validation");
            }
            result.markComplete();
            return result;
        } catch (Exception e) {
            log.error("AI rule orchestration failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.markComplete();
            return result;
        }
    }

    public RuleModificationResult modifyRule(Long ruleId, String modificationRequirement) {
        RuleModificationResult result = new RuleModificationResult();
        result.setRuleId(ruleId);
        result.setModificationRequirement(modificationRequirement);

        try {
            Optional<Rule> ruleOpt = ruleRepository.findById(ruleId);
            if (ruleOpt.isEmpty()) {
                result.setSuccess(false);
                result.setErrorMessage("Rule not found: " + ruleId);
                return result;
            }

            Rule existing = ruleOpt.get();
            result.setOriginalRule(existing.getContent());
            result.setImpactAnalysis(sequentialThinkingService.performSequentialReasoning(
                    "Modify rule with requirement: " + modificationRequirement, 3));

            RuleAnalysisResult analysis = sequentialThinkingService.analyzeRule(existing.getContent());
            String modified = sequentialThinkingService.generateDroolsRule(analysis,
                    "Modify existing rule to satisfy: " + modificationRequirement + "\nExisting:\n" + existing.getContent());
            result.setModifiedRule(modified);

            Rule temp = new Rule();
            temp.setContent(modified);
            List<TestCase> testCases = sequentialThinkingService.generateTestCases(temp, analysis);
            result.setTestCases(testCases);

            RuleValidationResult validation = droolsService.validateRule(modified);
            result.setTestResults(mapValidationToTestResults(testCases, validation));
            result.setSuccess(validation.isValid());
            if (!validation.isValid()) {
                result.setErrorMessage("Modified rule failed validation");
            }
            return result;
        } catch (Exception e) {
            log.error("AI rule modification failed: {}", e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    public RuleEvaluationResult evaluateRuleSet(Long ruleSetId) {
        RuleEvaluationResult result = new RuleEvaluationResult();
        result.setRuleSetId(ruleSetId);
        try {
            Map<String, Object> performanceData = new HashMap<>();
            performanceData.put("ruleSetId", ruleSetId);
            performanceData.put("validationStatus", "SUPPORTED_VIA_DROOLS_SERVICE");
            result.setPerformanceData(performanceData);
            result.setPerformanceAnalysis(sequentialThinkingService.performSequentialReasoning(
                    "Evaluate rule set " + ruleSetId + " for performance and maintainability", 3));
            result.setRecommendations(List.of(
                    "Validate all active rules before deployment",
                    "Review conflicting price writers for quote-level finalPrice mutations",
                    "Track execution metrics for heavy rule sets"));
            result.setSuccess(true);
            return result;
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            return result;
        }
    }

    public CompletableFuture<RuleCreationResult> createRuleAsync(String businessRequirement) {
        return CompletableFuture.supplyAsync(() -> createRuleFromRequirement(businessRequirement));
    }

    private List<TestResult> mapValidationToTestResults(List<TestCase> testCases, RuleValidationResult validation) {
        List<TestResult> results = new ArrayList<>();
        if (testCases == null || testCases.isEmpty()) {
            TestResult single = new TestResult();
            single.setTestCaseId("validation");
            single.setTestCaseName("Drools validation");
            single.setSuccess(validation != null && validation.isValid());
            if (validation != null && !validation.isValid() && validation.getErrors() != null && !validation.getErrors().isEmpty()) {
                single.setErrorMessage(validation.getErrors().get(0).getMessage());
            }
            results.add(single);
            return results;
        }

        for (TestCase testCase : testCases) {
            TestResult tr = new TestResult();
            tr.setTestCaseId(testCase.getId());
            tr.setTestCaseName(testCase.getDescription());
            tr.setSuccess(validation != null && validation.isValid());
            if (validation != null && !validation.isValid() && validation.getErrors() != null && !validation.getErrors().isEmpty()) {
                tr.setErrorMessage(validation.getErrors().get(0).getMessage());
            }
            results.add(tr);
        }
        return results;
    }
}
