package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class ProductSkuManagementAgentTest {

    private ProductSkuManagementAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ProductSkuManagementAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-013", agent.getAgentId());
        assertEquals("Product SKU Management Agent", agent.getName());
        assertEquals("Creating/maintaining individual SKUs and master data", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
