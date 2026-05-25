package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompatibilityRulesAgentTest {

    @Test
    void exposesAgentMetadata() {
        CompatibilityRulesAgent agent = new CompatibilityRulesAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
