package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OptionFeatureConfigurationAgentTest {

    private OptionFeatureConfigurationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new OptionFeatureConfigurationAgent();
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-002", agent.getAgentId());
        assertEquals("Option & Feature Configuration Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsSimulatedResponse() {
        AgentResponse response = agent.invoke(mock(AgentRequest.class));

        assertTrue(response.isSuccess());
        assertEquals("Simulated execution for Option & Feature Configuration Agent", response.getOutput());
        assertEquals("Semi-autonomous", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamReturnsFixedChunks() {
        Flux<String> flux = agent.stream(mock(AgentRequest.class));

        StepVerifier.create(flux)
                .expectNext("Starting stream for Option & Feature Configuration Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
