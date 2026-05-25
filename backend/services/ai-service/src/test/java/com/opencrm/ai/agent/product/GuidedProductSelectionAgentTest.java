package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class GuidedProductSelectionAgentTest {

    @Test
    void exposesAgentMetadata() {
        GuidedProductSelectionAgent agent = new GuidedProductSelectionAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
