package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CompetitivePricingIntelligenceAgentTest {

    private CompetitivePricingIntelligenceAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CompetitivePricingIntelligenceAgent();
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-034", agent.getAgentId());
        assertEquals("Competitive Pricing Intelligence Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsSimulatedResponse() {
        AgentResponse response = agent.invoke(mock(AgentRequest.class));

        assertTrue(response.isSuccess());
        assertEquals("Simulated execution for Competitive Pricing Intelligence Agent", response.getOutput());
        assertEquals("Advisory", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamReturnsFixedChunks() {
        Flux<String> flux = agent.stream(mock(AgentRequest.class));

        StepVerifier.create(flux)
                .expectNext("Starting stream for Competitive Pricing Intelligence Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
