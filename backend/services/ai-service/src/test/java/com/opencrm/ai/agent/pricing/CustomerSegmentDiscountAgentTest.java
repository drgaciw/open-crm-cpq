package com.opencrm.ai.agent.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CustomerSegmentDiscountAgentTest {

    @Test
    void exposesAgentMetadata() {
        CustomerSegmentDiscountAgent agent = new CustomerSegmentDiscountAgent();

        assertEquals("cpq-agent-008", agent.getAgentId());
        assertEquals("Customer Segment Discount Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
