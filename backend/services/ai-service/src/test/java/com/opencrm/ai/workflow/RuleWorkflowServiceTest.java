package com.opencrm.ai.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.ai.agent.DefaultAgentResponse;
import com.opencrm.ai.agent.rules.RuleCreateAgent;
import com.opencrm.ai.agent.rules.RuleModifyAgent;
import com.opencrm.ai.agent.rules.RuleOptimizeAgent;
import com.opencrm.ai.agent.rules.RulePerformanceAgent;
import com.opencrm.ai.agent.rules.RuleSetEvaluateAgent;
import com.opencrm.ai.registry.CpqAgentToolClients;
import com.opencrm.ai.registry.CpqToolResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleWorkflowServiceTest {

    @Mock RuleCreateAgent ruleCreateAgent;
    @Mock RuleModifyAgent ruleModifyAgent;
    @Mock RuleSetEvaluateAgent ruleSetEvaluateAgent;
    @Mock RuleOptimizeAgent ruleOptimizeAgent;
    @Mock RulePerformanceAgent rulePerformanceAgent;
    @Mock CpqAgentToolClients toolClients;

    private RuleWorkflowService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new RuleWorkflowService(
                ruleCreateAgent,
                ruleModifyAgent,
                ruleSetEvaluateAgent,
                ruleOptimizeAgent,
                rulePerformanceAgent,
                toolClients,
                objectMapper);
    }

    @Test
    void extractProposedDrlFromFence() {
        String drl = RuleWorkflowService.extractProposedDrl("""
                Here is a draft:
                ```drl
                rule "Volume Discount"
                when
                    $q : Quote()
                then
                    $q.setDiscount(0.1);
                end
                ```
                """);
        assertNotNull(drl);
        assertTrue(drl.contains("rule \"Volume Discount\""));
    }

    @Test
    void createRuleReturnsAdvisoryProposalWithoutPersistingByDefault() {
        when(ruleCreateAgent.invoke(any())).thenReturn(DefaultAgentResponse.success("""
                {"summary":"Draft ready","proposedDrl":"rule \\"X\\" when then end","requiresApproval":true,"suggestions":["Review"]}
                """, Map.of("agentId", "cpq-agent-038")));

        RuleWorkflowResult result = service.createRule(new CreateRuleWorkflowRequest(
                "Give 10% volume discount over 100 seats",
                "PRICING",
                "PRICING",
                9L,
                "Volume Draft",
                500,
                false));

        assertTrue(result.success());
        assertTrue(result.requiresApproval());
        assertFalse(result.draftPersisted());
        assertNull(result.draftRuleId());
        assertNotNull(result.proposedDrl());
        assertTrue(result.proposedDrl().contains("rule"));
    }

    @Test
    void createRulePersistsDraftAndValidatesWhenRequested() {
        when(ruleCreateAgent.invoke(any())).thenReturn(DefaultAgentResponse.success("""
                ```drl
                rule "Y" when then end
                ```
                """, Map.of()));

        ObjectNode created = objectMapper.createObjectNode();
        created.put("id", 77);
        created.put("status", "DRAFT");
        when(toolClients.createDraftRule(anyMap())).thenReturn(CpqToolResponse.ok(created, "created"));
        when(toolClients.validateRule(77L)).thenReturn(CpqToolResponse.ok(null, "validated"));

        RuleWorkflowResult result = service.createRule(new CreateRuleWorkflowRequest(
                "Floor margin at 30%",
                "PRICING",
                "PRICING",
                3L,
                "Margin Floor",
                400,
                true));

        assertTrue(result.draftPersisted());
        assertTrue(result.draftValidated());
        assertEquals(77L, result.draftRuleId());
        verify(toolClients).createDraftRule(anyMap());
        verify(toolClients).validateRule(77L);
    }

    @Test
    void evaluateRuleSetUsesImpactContext() {
        when(toolClients.listRulesByRuleSet(5L)).thenReturn(
                CpqToolResponse.ok(objectMapper.createArrayNode(), "ok"));
        when(ruleSetEvaluateAgent.invoke(any())).thenReturn(DefaultAgentResponse.success("""
                {"summary":"ok","findings":["Priority clash on rules 1 and 2"],"requiresApproval":false}
                """, Map.of()));

        RuleEvaluationWorkflowResult result = service.evaluateRuleSet(5L);

        assertTrue(result.success());
        assertEquals(5L, result.ruleSetId());
        assertEquals(1, result.findings().size());
    }

    @Test
    void analyzePerformanceLoadsImpact() {
        ObjectNode impact = objectMapper.createObjectNode();
        impact.put("pricingExecutionsWithRuleSetLast30Days", 12);
        when(toolClients.getRuleImpact(12L)).thenReturn(CpqToolResponse.ok(impact, "impact"));
        when(rulePerformanceAgent.invoke(any())).thenReturn(DefaultAgentResponse.success("""
                {"issues":["Hot path"],"recommendations":["Add explanation code"]}
                """, Map.of()));

        PerformanceAnalysisResult result = service.analyzePerformance(12L);

        assertTrue(result.success());
        assertEquals("Hot path", result.issues().getFirst());
        verify(toolClients).getRuleImpact(anyLong());
    }
}
