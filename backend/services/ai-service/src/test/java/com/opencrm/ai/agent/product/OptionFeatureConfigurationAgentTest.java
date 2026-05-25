package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class OptionFeatureConfigurationAgentTest {

    private OptionFeatureConfigurationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new OptionFeatureConfigurationAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-002", agent.getAgentId());
        assertEquals("Option & Feature Configuration Agent", agent.getName());
        assertEquals("Selecting configurable options, attributes, and features with rule validation", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
