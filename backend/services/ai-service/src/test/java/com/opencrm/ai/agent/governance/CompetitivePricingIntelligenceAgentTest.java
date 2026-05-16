package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompetitivePricingIntelligenceAgentTest {

    @Test
    void exposesAgentMetadata() {
        CompetitivePricingIntelligenceAgent agent = new CompetitivePricingIntelligenceAgent();

        assertEquals("cpq-agent-034", agent.getAgentId());
        assertEquals("Competitive Pricing Intelligence Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
