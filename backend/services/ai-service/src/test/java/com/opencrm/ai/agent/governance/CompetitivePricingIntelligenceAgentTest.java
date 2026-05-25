package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class CompetitivePricingIntelligenceAgentTest {

    @Test
    void exposesAgentMetadata() {
        CompetitivePricingIntelligenceAgent agent = new CompetitivePricingIntelligenceAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
