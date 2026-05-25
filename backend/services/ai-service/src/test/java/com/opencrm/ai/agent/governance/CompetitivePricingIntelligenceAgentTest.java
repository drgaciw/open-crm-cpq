package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class CompetitivePricingIntelligenceAgentTest {

    private CompetitivePricingIntelligenceAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CompetitivePricingIntelligenceAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-034", agent.getAgentId());
        assertEquals("Competitive Pricing Intelligence Agent", agent.getName());
        assertEquals("Competitive pricing analysis and positioning", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
