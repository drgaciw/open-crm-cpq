package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.opencrm.ai.agent.AbstractCpqAgent;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyFxManagementAgentTest {

    private CurrencyFxManagementAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CurrencyFxManagementAgent();
    }

    @Test
    void testAgentProperties() {
        assertEquals("cpq-agent-032", agent.getAgentId());
        assertEquals("Currency & FX Management Agent", agent.getName());
        assertEquals("Multi-currency pricing and FX rate management", agent.getDescription());
        assertTrue(agent instanceof AbstractCpqAgent);
    }
}
