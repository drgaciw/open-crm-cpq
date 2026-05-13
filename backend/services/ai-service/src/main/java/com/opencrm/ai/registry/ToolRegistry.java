package com.opencrm.ai.registry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import java.util.function.Function;

@Configuration
public class ToolRegistry {

    @Bean
    @Description("Query the CRM Customer database by Segment")
    public Function<CustomerSegmentRequest, CustomerSegmentResponse> customerSegmentTool() {
        return request -> new CustomerSegmentResponse("Simulated Response: 45 enterprise customers");
    }

    @Bean
    @Description("Check pricing margin threshold guardrails")
    public Function<MarginCheckRequest, MarginCheckResponse> marginCheckTool() {
        return request -> new MarginCheckResponse("Simulated Response: Margin is acceptable (35%)");
    }

    @Bean
    @Description("Generate a Drools pricing rule from natural language requirements")
    public Function<GenerateRuleRequest, RuleToolResponse> generateRuleTool() {
        return request -> new RuleToolResponse(
                request.ruleName(),
                "GENERATED",
                "Generated Drools rule for " + request.requirement()
        );
    }

    @Bean
    @Description("Optimize an existing Drools pricing rule")
    public Function<OptimizeRuleRequest, RuleToolResponse> optimizeRuleTool() {
        return request -> new RuleToolResponse(
                request.ruleName(),
                "OPTIMIZED",
                "Optimized rule with " + request.goal()
        );
    }

    @Bean
    @Description("Analyze a Drools pricing rule for behavior and risks")
    public Function<AnalyzeRuleRequest, RuleToolResponse> analyzeRuleTool() {
        return request -> new RuleToolResponse(
                request.ruleName(),
                "ANALYZED",
                "Analysis completed for rule length " + request.drl().length()
        );
    }

    @Bean
    @Description("Validate a Drools pricing rule for syntax and policy readiness")
    public Function<ValidateRuleRequest, RuleValidationResponse> validateRuleTool() {
        boolean valid = request.drl() != null && request.drl().contains("rule") && request.drl().contains("end");
        return new RuleValidationResponse(request.ruleName(), valid, valid ? "Rule is valid" : "Rule is invalid");
    }

    @Bean
    @Description("Generate executable test cases for a Drools pricing rule")
    public Function<GenerateTestCasesRequest, RuleToolResponse> generateTestCasesTool() {
        return request -> new RuleToolResponse(
                request.ruleName(),
                "TEST_CASES_GENERATED",
                "Generated " + request.scenarioCount() + " test cases"
        );
    }

    public record CustomerSegmentRequest(String segment) {}
    public record CustomerSegmentResponse(String result) {}
    
    public record MarginCheckRequest(double cost, double price) {}
    public record MarginCheckResponse(String result) {}

    public record GenerateRuleRequest(String ruleName, String requirement) {}
    public record OptimizeRuleRequest(String ruleName, String drl, String goal) {}
    public record AnalyzeRuleRequest(String ruleName, String drl) {}
    public record ValidateRuleRequest(String ruleName, String drl) {}
    public record GenerateTestCasesRequest(String ruleName, String drl, int scenarioCount) {}
    public record RuleToolResponse(String ruleName, String status, String result) {}
    public record RuleValidationResponse(String ruleName, boolean valid, String result) {}
}
