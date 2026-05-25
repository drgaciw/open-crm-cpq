package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptionFeatureConfigurationAgentTest {

    @Test
    void exposesAgentMetadata() {
        OptionFeatureConfigurationAgent agent = new OptionFeatureConfigurationAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
