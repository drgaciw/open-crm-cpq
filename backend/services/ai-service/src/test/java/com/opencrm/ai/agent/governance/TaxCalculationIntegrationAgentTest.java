package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
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
        assertEquals("Simulated execution for Tax Calculation Integration Agent", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamReturnsFixedChunks() {
        Flux<String> flux = agent.stream(mock(AgentRequest.class));

        StepVerifier.create(flux)
                .expectNext("Starting stream for Tax Calculation Integration Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
