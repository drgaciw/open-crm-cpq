package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MarkupCostIntegrationAgentTest {

    private MarkupCostIntegrationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new MarkupCostIntegrationAgent();
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-006", agent.getAgentId());
        assertEquals("Markup & Cost Integration Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsSimulatedResponse() {
        AgentResponse response = agent.invoke(mock(AgentRequest.class));
        assertTrue(response.isSuccess());
        assertTrue(response.getOutput().contains("Simulated execution"));
        assertEquals("Semi-autonomous", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamStubSequence() {
        StepVerifier.create(agent.stream(mock(AgentRequest.class)))
                .expectNextMatches(s -> s.contains("Markup & Cost Integration Agent"))
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
