package com.opencrm.ai.agent.pricing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.agent.AbstractCpqAgent;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import com.opencrm.ai.agent.DefaultAgentRequest;
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

class PilotPricingAgentsModelBackedTest {

    @Test
    void pilotPricingAgentsUseSharedModelBackedAdapter() {
        assertInstanceOf(AbstractCpqAgent.class, new VolumeDiscountAgent());
        assertInstanceOf(AbstractCpqAgent.class, new CustomerSegmentDiscountAgent());
        assertInstanceOf(AbstractCpqAgent.class, new MinimumMarginEnforcementAgent());
    }

    @Test
    void pilotPricingAgentsDelegateToLlmAdapterWithAutomatedConstraintsMetadata() {
        for (CpqAgent agent : List.of(
                new VolumeDiscountAgent(),
                new CustomerSegmentDiscountAgent(),
                new MinimumMarginEnforcementAgent())) {
            LlmAdapter llmAdapter = mock(LlmAdapter.class);
            when(llmAdapter.generate(anyString(), anyString())).thenReturn("{\"summary\":\"ok\"}");

            injectBaseDependencies(agent, llmAdapter);

            AgentResponse response = agent.invoke(DefaultAgentRequest.of(
                    "Evaluate this pricing step.",
                    Map.of("sessionId", "session-1", "quoteId", "quote-123")));

            assertTrue(response.isSuccess());
            assertEquals("{\"summary\":\"ok\"}", response.getOutput());
            assertEquals(agent.getAgentId(), response.getMetadata().get("agentId"));
            assertEquals("Automated with constraints", response.getMetadata().get("autonomy"));
            verify(llmAdapter).generate(anyString(), anyString());
        }
    }

    private static void injectBaseDependencies(CpqAgent agent, LlmAdapter llmAdapter) {
        ReflectionTestUtils.setField(agent, "llmAdapter", llmAdapter);
        ReflectionTestUtils.setField(agent, "auditLogger", mock(AgentAuditLogger.class));
        ReflectionTestUtils.setField(agent, "objectMapper", new ObjectMapper());
    }
}
