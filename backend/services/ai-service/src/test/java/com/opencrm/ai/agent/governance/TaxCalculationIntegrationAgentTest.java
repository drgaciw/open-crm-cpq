package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TaxCalculationIntegrationAgentTest {

    private TaxCalculationIntegrationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new TaxCalculationIntegrationAgent();
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-033", agent.getAgentId());
        assertEquals("Tax Calculation Integration Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsSimulatedResponse() {
        AgentResponse response = agent.invoke(mock(AgentRequest.class));
        assertTrue(response.isSuccess());
        assertTrue(response.getOutput().contains("Simulated execution"));
        assertEquals("Automated", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamStubSequence() {
        StepVerifier.create(agent.stream(mock(AgentRequest.class)))
                .expectNextMatches(s -> s.contains("Tax Calculation Integration Agent"))
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
