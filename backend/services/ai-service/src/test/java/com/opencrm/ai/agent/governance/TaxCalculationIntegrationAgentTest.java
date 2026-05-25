package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaxCalculationIntegrationAgentTest {

    @Test
    void exposesAgentMetadata() {
        TaxCalculationIntegrationAgent agent = new TaxCalculationIntegrationAgent();

        assertNotNull(agent.getAgentId());
        assertNotNull(agent.getName());
        assertNotNull(agent.getDescription());
    }
}
