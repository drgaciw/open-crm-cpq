package com.opencrm.ai.agent.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCreateAgentTest {

    @Test
    void exposesAgentMetadata() {
        RuleCreateAgent agent = new RuleCreateAgent();
        assertEquals("cpq-agent-038", agent.getAgentId());
        assertEquals("Rule Create Agent", agent.getName());
        assertNotNull(agent.getDescription());
        assertTrue(agent.getDescription().toLowerCase().contains("drools"));
    }
}
