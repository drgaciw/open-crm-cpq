package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-032")
public class CurrencyFxManagementAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-032";
    }

    @Override
    public String getName() {
        return "Currency & FX Management Agent";
    }

    @Override
    public String getDescription() {
        return "Multi-currency pricing and FX rate management";
    }

    @Override
    protected String autonomyTier() {
        return "Automated";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-032, the Currency & FX Management Agent.

                Responsibilities:
                - Multi-currency pricing and FX rate management
                """;
    }
}