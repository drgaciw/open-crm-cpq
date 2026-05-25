package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class CurrencyFxManagementAgentTest {

    @Test
    void exposesAgentMetadata() {
        CurrencyFxManagementAgent agent = new CurrencyFxManagementAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
