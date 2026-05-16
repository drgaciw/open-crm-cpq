package com.opencrm.ai.agent.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VolumeDiscountAgentTest {

    @Test
    void exposesAgentMetadata() {
        VolumeDiscountAgent agent = new VolumeDiscountAgent();

        assertEquals("cpq-agent-007", agent.getAgentId());
        assertEquals("Volume Discount Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
