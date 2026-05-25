package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-033")
public class TaxCalculationIntegrationAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-033";
    }

    @Override
    public String getName() {
        return "Tax Calculation Integration Agent";
    }

    @Override
    public String getDescription() {
        return "Tax rate application via external tax services";
    }

    @Override
    protected String autonomyTier() {
        return "Automated";
    }

    @Override
    protected String agentSystemPrompt() {
        return "Tax rate application via external tax services";
    }
}
