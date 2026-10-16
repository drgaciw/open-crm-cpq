package com.opencrm.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.agent.governance.CompetitivePricingIntelligenceAgent;
import com.opencrm.ai.agent.governance.CurrencyFxManagementAgent;
import com.opencrm.ai.agent.governance.TaxCalculationIntegrationAgent;
import com.opencrm.ai.agent.product.BundleAssemblyAgent;
import com.opencrm.ai.agent.product.CompatibilityRulesAgent;
import com.opencrm.ai.agent.product.GuidedProductSelectionAgent;
import com.opencrm.ai.agent.product.OptionFeatureConfigurationAgent;
import com.opencrm.ai.agent.product.ProductSkuManagementAgent;
import com.opencrm.ai.audit.AgentAuditLogger;
import com.opencrm.ai.llm.LlmAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainAgentsModelBackedTest {

    private static final List<CpqAgent> DOMAIN_AGENTS = List.of(
            new GuidedProductSelectionAgent(),
            new OptionFeatureConfigurationAgent(),
            new BundleAssemblyAgent(),
            new ProductSkuManagementAgent(),
            new CompatibilityRulesAgent(),
            new CurrencyFxManagementAgent(),
            new TaxCalculationIntegrationAgent(),
            new CompetitivePricingIntelligenceAgent()
    );

    @Test
    void domainAgentsUseSharedModelBackedBase() {
        DOMAIN_AGENTS.forEach(agent -> assertInstanceOf(AbstractCpqAgent.class, agent));
    }

    @Test
    void domainAgentsDelegateToLlmAdapterWithAgentMetadata() {
        for (CpqAgent agent : DOMAIN_AGENTS) {
            LlmAdapter llmAdapter = mock(LlmAdapter.class);
            when(llmAdapter.generate(anyString(), anyString())).thenReturn("{\"summary\":\"ok\"}");

            injectBaseDependencies(agent, llmAdapter);

            AgentResponse response = agent.invoke(DefaultAgentRequest.of(
                    "Evaluate this CPQ step.",
                    Map.of("sessionId", "session-1", "quoteId", "quote-123")));

            assertTrue(response.isSuccess());
            assertEquals("{\"summary\":\"ok\"}", response.getOutput());
            assertEquals(agent.getAgentId(), response.getMetadata().get("agentId"));
            assertTrue(response.getMetadata().containsKey("autonomy"));
            verify(llmAdapter).generate(anyString(), anyString());
        }
    }

    private static void injectBaseDependencies(CpqAgent agent, LlmAdapter llmAdapter) {
        ReflectionTestUtils.setField(agent, "llmAdapter", llmAdapter);
        ReflectionTestUtils.setField(agent, "auditLogger", mock(AgentAuditLogger.class));
        ReflectionTestUtils.setField(agent, "objectMapper", new ObjectMapper());
    }
}
