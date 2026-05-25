package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundleAssemblyAgentTest {

    @Test
    void exposesAgentMetadata() {
        BundleAssemblyAgent agent = new BundleAssemblyAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
