package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GuidedProductSelectionAgentTest {

    @Test
    void exposesAgentMetadata() {
        GuidedProductSelectionAgent agent = new GuidedProductSelectionAgent();

        assertEquals("cpq-agent-001", agent.getAgentId());
        assertEquals("Guided Product Selection Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
