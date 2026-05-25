package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class CompatibilityRulesAgentTest {

    private CompatibilityRulesAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CompatibilityRulesAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-015", agent.getAgentId());
        assertEquals("Compatibility Rules Agent", agent.getName());
        assertEquals("Enforcing technical/product compatibility constraints", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
