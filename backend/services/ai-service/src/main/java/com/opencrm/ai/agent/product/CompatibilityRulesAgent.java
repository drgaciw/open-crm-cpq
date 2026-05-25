package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-015")
public class CompatibilityRulesAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-015";
    }

    @Override
    public String getName() {
        return "Compatibility Rules Agent";
    }

    @Override
    public String getDescription() {
        return "Enforcing technical/product compatibility constraints";
    }

    @Override
    protected String autonomyTier() {
        return "Semi-autonomous";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-015, the Compatibility Rules Agent.

                Responsibilities:
                - Enforcing technical/product compatibility constraints
                """;
    }
}