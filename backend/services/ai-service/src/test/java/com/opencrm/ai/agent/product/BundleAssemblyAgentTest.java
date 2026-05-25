package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class BundleAssemblyAgentTest {

    private BundleAssemblyAgent agent;

    @BeforeEach
    void setUp() {
        agent = new BundleAssemblyAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-003", agent.getAgentId());
        assertEquals("Bundle Assembly Agent", agent.getName());
        assertEquals("Creating and modifying product bundles with constraint enforcement", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
