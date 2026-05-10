package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class ProductSkuManagementAgentTest {

    private ProductSkuManagementAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ProductSkuManagementAgent();
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-013", agent.getAgentId());
        assertEquals("Product SKU Management Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsSimulatedResponse() {
        AgentResponse response = agent.invoke(mock(AgentRequest.class));
        assertTrue(response.isSuccess());
        assertTrue(response.getOutput().contains("Simulated execution"));
        assertEquals("Advisory", response.getMetadata().get("autonomy"));
    }

    @Test
    void testStreamStubSequence() {
        StepVerifier.create(agent.stream(mock(AgentRequest.class)))
                .expectNextMatches(s -> s.contains("Product SKU Management Agent"))
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
