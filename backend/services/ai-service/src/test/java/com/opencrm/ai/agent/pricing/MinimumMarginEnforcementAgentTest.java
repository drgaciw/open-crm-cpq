package com.opencrm.ai.agent.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MinimumMarginEnforcementAgentTest {

    @Test
    void exposesAgentMetadata() {
        MinimumMarginEnforcementAgent agent = new MinimumMarginEnforcementAgent();

        assertEquals("cpq-agent-011", agent.getAgentId());
        assertEquals("Minimum Margin Enforcement Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
