package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptionFeatureConfigurationAgentTest {

    @Test
    void exposesAgentMetadata() {
        OptionFeatureConfigurationAgent agent = new OptionFeatureConfigurationAgent();

        assertEquals("cpq-agent-002", agent.getAgentId());
        assertEquals("Option & Feature Configuration Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
