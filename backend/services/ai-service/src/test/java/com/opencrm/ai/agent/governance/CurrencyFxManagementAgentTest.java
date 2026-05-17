package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CurrencyFxManagementAgentTest {

    private CurrencyFxManagementAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CurrencyFxManagementAgent();
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-032", agent.getAgentId());
        assertEquals("Currency & FX Management Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsSimulatedResponse() {
        AgentResponse response = agent.invoke(mock(AgentRequest.class));

        assertTrue(response.isSuccess());
        assertEquals("Simulated execution for Currency & FX Management Agent", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamReturnsFixedChunks() {
        Flux<String> flux = agent.stream(mock(AgentRequest.class));

        StepVerifier.create(flux)
                .expectNext("Starting stream for Currency & FX Management Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
