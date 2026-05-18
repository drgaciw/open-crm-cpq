package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GuidedProductSelectionAgentTest {

    private GuidedProductSelectionAgent agent;

    @BeforeEach
    void setUp() {
        agent = new GuidedProductSelectionAgent();
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-001", agent.getAgentId());
        assertEquals("Guided Product Selection Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsSimulatedResponse() {
        AgentResponse response = agent.invoke(mock(AgentRequest.class));

        assertTrue(response.isSuccess());
        assertEquals("Simulated execution for Guided Product Selection Agent", response.getOutput());
        assertEquals("Advisory", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamReturnsFixedChunks() {
        Flux<String> flux = agent.stream(mock(AgentRequest.class));

        StepVerifier.create(flux)
                .expectNext("Starting stream for Guided Product Selection Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
