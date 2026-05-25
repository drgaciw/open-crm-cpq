package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-013")
public class ProductSkuManagementAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-013";
    }

    @Override
    public String getName() {
        return "Product SKU Management Agent";
    }

    @Override
    public String getDescription() {
        return "Creating/maintaining individual SKUs and master data";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-013, the Product SKU Management Agent.

                Responsibilities:
                - Creating/maintaining individual SKUs and master data
                """;
    }
}