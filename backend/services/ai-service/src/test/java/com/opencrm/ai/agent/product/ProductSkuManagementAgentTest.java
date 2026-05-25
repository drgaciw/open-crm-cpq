package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductSkuManagementAgentTest {

    @Test
    void exposesAgentMetadata() {
        ProductSkuManagementAgent agent = new ProductSkuManagementAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
