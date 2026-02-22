package com.opencrm.pricingairules.ai.sequential;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricingairules.ai.prompt.PromptService;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.service.fallback.AIFallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SequentialThinkingService {

    private final ObjectProvider<ChatClient> chatClientProvider;
    private final RetryTemplate pricingAiRulesRetryTemplate;
    private final PromptService promptService;
    private final AIFallbackService fallbackService;
    private final ObjectMapper objectMapper;

    @Value("${pricing-ai-rules.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${pricing-ai-rules.ai.max-retries:2}")
    private int maxRetries;

    @Value("${drools.ai.sequential-thinking.enabled:true}")
    private boolean sequentialThinkingEnabled;

    public String performSequentialReasoning(String prompt, int totalSteps) {
        if (!isAiAvailable()) {
            return "AI sequential thinking unavailable. Fallback mode active.\n\n" + prompt;
        }

        List<String> thoughts = new ArrayList<>();
        for (int i = 1; i <= Math.max(totalSteps, 1); i++) {
            Map<String, Object> vars = new HashMap<>();
            vars.put("thought", prompt);
            vars.put("thoughtNumber", i);
            vars.put("totalThoughts", totalSteps);
            vars.put("isRevision", "");
            vars.put("nextStepInfo", i == totalSteps ? "Final step" : "More steps remain");
            String userPrompt = promptService.renderPrompt("sequential-thinking", "step-template", vars);
            thoughts.add(callModel(promptService.getSystemPrompt("sequential-thinking"), userPrompt));
        }
        return String.join("\n\n", thoughts);
    }

    public RuleAnalysisResult analyzeBusinessRequirement(String businessRequirement) {
        if (!isAiAvailable()) {
            return fallbackService.fallbackRuleAnalysis(businessRequirement);
        }

        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("requirement", businessRequirement);
            vars.put("format", "Return valid JSON.");
            String userPrompt = promptService.renderPrompt("rule-analysis", "analyze-requirement", vars);
            String response = callModel(promptService.getSystemPrompt("rule-analysis"), userPrompt);
            return objectMapper.readValue(response, RuleAnalysisResult.class);
        } catch (Exception e) {
            log.warn("AI analysis parse failure, falling back: {}", e.getMessage());
            return fallbackService.fallbackRuleAnalysis(businessRequirement);
        }
    }

    public String generateDroolsRule(RuleAnalysisResult analysis, String businessRequirement) {
        if (!isAiAvailable()) {
            return fallbackService.fallbackRuleContent(businessRequirement);
        }

        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("businessRequirement", businessRequirement);
            vars.put("conditions", join(analysis.getConditions()));
            vars.put("actions", join(analysis.getActions()));
            vars.put("requiredInputs", join(analysis.getRequiredInputs()));
            vars.put("expectedOutputs", join(analysis.getExpectedOutputs()));
            String userPrompt = promptService.renderPrompt("rule-generation", "generate-rule", vars);
            return callModel(promptService.getSystemPrompt("rule-generation"), userPrompt);
        } catch (Exception e) {
            log.warn("AI rule generation failed, using fallback: {}", e.getMessage());
            return fallbackService.fallbackRuleContent(businessRequirement);
        }
    }

    public List<TestCase> generateTestCases(Rule rule, RuleAnalysisResult analysis) {
        if (!isAiAvailable()) {
            return fallbackService.fallbackTestCases(rule.getContent());
        }

        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("ruleContent", rule.getContent());
            vars.put("conditions", join(analysis.getConditions()));
            vars.put("actions", join(analysis.getActions()));
            vars.put("requiredInputs", join(analysis.getRequiredInputs()));
            vars.put("expectedOutputs", join(analysis.getExpectedOutputs()));
            vars.put("testCount", 5);
            vars.put("format", "Return valid JSON array.");
            String userPrompt = promptService.renderPrompt("test-generation", "generate-tests", vars);
            String response = callModel(promptService.getSystemPrompt("test-generation"), userPrompt);
            List<TestCase> testCases = objectMapper.readValue(response, new TypeReference<>() {});
            return (testCases == null || testCases.isEmpty())
                    ? fallbackService.fallbackTestCases(rule.getContent())
                    : testCases;
        } catch (Exception e) {
            log.warn("AI test generation failed, using fallback: {}", e.getMessage());
            return fallbackService.fallbackTestCases(rule.getContent());
        }
    }

    public List<TestCase> generateTestCases(String ruleContent, String coverageLevel) {
        Rule tempRule = new Rule();
        tempRule.setContent(ruleContent);
        RuleAnalysisResult analysis = analyzeRule(ruleContent);
        return generateTestCases(tempRule, analysis);
    }

    public String optimizeRule(Rule rule) {
        if (!isAiAvailable()) {
            return rule.getContent();
        }
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("ruleContent", rule.getContent());
            vars.put("executionTime", "N/A");
            vars.put("memoryUsage", "N/A");
            vars.put("conditionEvaluations", "N/A");
            vars.put("cacheHitRate", "N/A");
            String userPrompt = promptService.renderPrompt("optimization", "optimize-rule", vars);
            String optimized = callModel(promptService.getSystemPrompt("optimization"), userPrompt);
            if (optimized == null || !optimized.contains("rule") || !optimized.contains("when") || !optimized.contains("then")) {
                return rule.getContent();
            }
            return optimized;
        } catch (Exception e) {
            log.warn("AI optimization failed, using original rule: {}", e.getMessage());
            return rule.getContent();
        }
    }

    public RuleAnalysisResult analyzeRule(String ruleContent) {
        return analyzeBusinessRequirement("Analyze this Drools rule and summarize conditions/actions:\n" + ruleContent);
    }

    private boolean isAiAvailable() {
        return aiEnabled && sequentialThinkingEnabled && chatClientProvider.getIfAvailable() != null;
    }

    private String join(List<String> values) {
        return values == null ? "" : String.join(", ", values);
    }

    private String callModel(String systemPrompt, String userPrompt) {
        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            throw new IllegalStateException("No ChatClient bean available");
        }

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        ));

        return pricingAiRulesRetryTemplate.execute(ctx -> {
            try {
                return chatClient.call(prompt).getResult().getOutput().getContent();
            } catch (Exception e) {
                if (ctx.getRetryCount() + 1 >= maxRetries) {
                    throw e;
                }
                throw e;
            }
        });
    }
}
