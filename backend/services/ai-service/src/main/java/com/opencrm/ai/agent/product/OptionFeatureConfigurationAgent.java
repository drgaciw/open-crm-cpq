package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-002")
public class OptionFeatureConfigurationAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-002";
    }

    @Override
    public String getName() {
        return "Option & Feature Configuration Agent";
    }

    @Override
    public String getDescription() {
        return "Selecting configurable options, attributes, and features with rule validation";
    }

    @Override
    protected String autonomyTier() {
        return "Semi-autonomous";
    }

    @Override
    protected String agentSystemPrompt() {
        return "Selecting configurable options, attributes, and features with rule validation";
    }
}
