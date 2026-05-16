package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CurrencyFxManagementAgentTest {

    @Test
    void exposesAgentMetadata() {
        CurrencyFxManagementAgent agent = new CurrencyFxManagementAgent();

        assertEquals("cpq-agent-032", agent.getAgentId());
        assertEquals("Currency & FX Management Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
