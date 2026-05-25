package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class TaxCalculationIntegrationAgentTest {

    private TaxCalculationIntegrationAgent agent;

    @BeforeEach
    void setUp() {
        agent = new TaxCalculationIntegrationAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-033", agent.getAgentId());
        assertEquals("Tax Calculation Integration Agent", agent.getName());
        assertEquals("Tax rate application via external tax services", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
