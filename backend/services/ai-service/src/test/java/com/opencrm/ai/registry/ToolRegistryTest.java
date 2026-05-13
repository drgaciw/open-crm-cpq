package com.opencrm.ai.registry;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.function.Function;

class ToolRegistryTest {

    private final ToolRegistry toolRegistry = new ToolRegistry();

    @Test
    void testCustomerSegmentTool() {
        Function<ToolRegistry.CustomerSegmentRequest, ToolRegistry.CustomerSegmentResponse> tool = toolRegistry.customerSegmentTool();

        ToolRegistry.CustomerSegmentRequest request = new ToolRegistry.CustomerSegmentRequest("enterprise");
        ToolRegistry.CustomerSegmentResponse response = tool.apply(request);

        assertNotNull(response, "Response should not be null");
        assertEquals("Simulated Response: 45 enterprise customers", response.result(), "Response result should match simulated output");
    }

    @Test
    void testMarginCheckTool() {
        Function<ToolRegistry.MarginCheckRequest, ToolRegistry.MarginCheckResponse> tool = toolRegistry.marginCheckTool();

        ToolRegistry.MarginCheckRequest request = new ToolRegistry.MarginCheckRequest(100.0, 150.0);
        ToolRegistry.MarginCheckResponse response = tool.apply(request);

        assertNotNull(response, "Response should not be null");
        assertEquals("Simulated Response: Margin is acceptable (35%)", response.result(), "Response result should match simulated output");
    }

    @Test
    void testGenerateRuleTool() {
        Function<ToolRegistry.GenerateRuleRequest, ToolRegistry.RuleToolResponse> tool = toolRegistry.generateRuleTool();

        ToolRegistry.RuleToolResponse response = tool.apply(new ToolRegistry.GenerateRuleRequest(
                "High Value Discount",
                "apply a five percent discount for quotes over 100000"
        ));

        assertNotNull(response, "Response should not be null");
        assertEquals("High Value Discount", response.ruleName());
        assertEquals("GENERATED", response.status());
        assertEquals("Generated Drools rule for apply a five percent discount for quotes over 100000", response.result());
    }

    @Test
    void testOptimizeRuleTool() {
        Function<ToolRegistry.OptimizeRuleRequest, ToolRegistry.RuleToolResponse> tool = toolRegistry.optimizeRuleTool();

        ToolRegistry.RuleToolResponse response = tool.apply(new ToolRegistry.OptimizeRuleRequest(
                "High Value Discount",
                "rule \"High Value Discount\" when then end",
                "remove duplicate conditions"
        ));

        assertNotNull(response, "Response should not be null");
        assertEquals("High Value Discount", response.ruleName());
        assertEquals("OPTIMIZED", response.status());
        assertEquals("Optimized rule with remove duplicate conditions", response.result());
    }

    @Test
    void testAnalyzeRuleTool() {
        Function<ToolRegistry.AnalyzeRuleRequest, ToolRegistry.RuleToolResponse> tool = toolRegistry.analyzeRuleTool();
        String drl = "rule \"High Value Discount\" when then end";

        ToolRegistry.RuleToolResponse response = tool.apply(new ToolRegistry.AnalyzeRuleRequest("High Value Discount", drl));

        assertNotNull(response, "Response should not be null");
        assertEquals("High Value Discount", response.ruleName());
        assertEquals("ANALYZED", response.status());
        assertEquals("Analysis completed for rule length " + drl.length(), response.result());
    }

    @Test
    void testValidateRuleTool() {
        Function<ToolRegistry.ValidateRuleRequest, ToolRegistry.RuleValidationResponse> tool = toolRegistry.validateRuleTool();

        ToolRegistry.RuleValidationResponse response = tool.apply(new ToolRegistry.ValidateRuleRequest(
                "High Value Discount",
                "rule \"High Value Discount\" when then end"
        ));

        assertNotNull(response, "Response should not be null");
        assertEquals("High Value Discount", response.ruleName());
        assertTrue(response.valid());
        assertEquals("Rule is valid", response.result());
    }

    @Test
    void testGenerateTestCasesTool() {
        Function<ToolRegistry.GenerateTestCasesRequest, ToolRegistry.RuleToolResponse> tool = toolRegistry.generateTestCasesTool();

        ToolRegistry.RuleToolResponse response = tool.apply(new ToolRegistry.GenerateTestCasesRequest(
                "High Value Discount",
                "rule \"High Value Discount\" when then end",
                3
        ));

        assertNotNull(response, "Response should not be null");
        assertEquals("High Value Discount", response.ruleName());
        assertEquals("TEST_CASES_GENERATED", response.status());
        assertEquals("Generated 3 test cases", response.result());
    }
}
