package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductSkuManagementAgentTest {

    @Test
    void exposesAgentMetadata() {
        ProductSkuManagementAgent agent = new ProductSkuManagementAgent();

        assertEquals("cpq-agent-013", agent.getAgentId());
        assertEquals("Product SKU Management Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
