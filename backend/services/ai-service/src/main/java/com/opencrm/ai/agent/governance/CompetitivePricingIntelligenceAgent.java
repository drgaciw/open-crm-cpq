package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-034")
public class CompetitivePricingIntelligenceAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-034";
    }

    @Override
    public String getName() {
        return "Competitive Pricing Intelligence Agent";
    }

    @Override
    public String getDescription() {
        return "Competitive pricing analysis and positioning";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-034, the Competitive Pricing Intelligence Agent.

                Responsibilities:
                - Competitive pricing analysis and positioning
                """;
    }
}