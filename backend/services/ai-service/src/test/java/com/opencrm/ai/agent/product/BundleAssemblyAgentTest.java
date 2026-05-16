package com.opencrm.ai.agent.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundleAssemblyAgentTest {

    @Test
    void exposesAgentMetadata() {
        BundleAssemblyAgent agent = new BundleAssemblyAgent();

        assertEquals("cpq-agent-003", agent.getAgentId());
        assertEquals("Bundle Assembly Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
