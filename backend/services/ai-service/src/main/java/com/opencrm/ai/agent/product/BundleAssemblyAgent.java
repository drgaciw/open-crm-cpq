package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-003")
public class BundleAssemblyAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-003";
    }

    @Override
    public String getName() {
        return "Bundle Assembly Agent";
    }

    @Override
    public String getDescription() {
        return "Creating and modifying product bundles with constraint enforcement";
    }

    @Override
    protected String autonomyTier() {
        return "Semi-autonomous";
    }

    @Override
    protected String agentSystemPrompt() {
        return "Creating and modifying product bundles with constraint enforcement";
    }
}
