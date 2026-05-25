package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-001")
public class GuidedProductSelectionAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-001";
    }

    @Override
    public String getName() {
        return "Guided Product Selection Agent";
    }

    @Override
    public String getDescription() {
        return "Step-by-step product search and recommendation during quote creation";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return "Step-by-step product search and recommendation during quote creation";
    }
}
