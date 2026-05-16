package com.opencrm.ai.agent.governance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TaxCalculationIntegrationAgentTest {

    @Test
    void exposesAgentMetadata() {
        TaxCalculationIntegrationAgent agent = new TaxCalculationIntegrationAgent();

        assertEquals("cpq-agent-033", agent.getAgentId());
        assertEquals("Tax Calculation Integration Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }
}
