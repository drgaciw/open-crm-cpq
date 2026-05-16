package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompatibilityRulesAgentTest {

    @Test
    void exposesAgentMetadata() {
        CompatibilityRulesAgent agent = new CompatibilityRulesAgent();

        assertEquals("cpq-agent-015", agent.getAgentId());
        assertEquals("Compatibility Rules Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
