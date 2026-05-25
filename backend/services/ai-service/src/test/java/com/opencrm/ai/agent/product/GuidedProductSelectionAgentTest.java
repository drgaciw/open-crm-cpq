package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class GuidedProductSelectionAgentTest {

    private GuidedProductSelectionAgent agent;

    @BeforeEach
    void setUp() {
        agent = new GuidedProductSelectionAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-001", agent.getAgentId());
        assertEquals("Guided Product Selection Agent", agent.getName());
        assertEquals("Step-by-step product search and recommendation during quote creation", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
