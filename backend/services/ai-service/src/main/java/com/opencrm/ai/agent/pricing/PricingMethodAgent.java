package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-005")
public class PricingMethodAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-005";
    }

    @Override
    public String getName() {
        return "Pricing Method Agent";
    }

    @Override
    public String getDescription() {
        return "Apply block, percent-of-total, cost-plus, and custom pricing methods";
    }

    @Override
    protected String autonomyTier() {
        return "Automated with constraints";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-005, the Pricing Method Agent.

                Responsibilities:
                - Apply the pricing method configured for each line: block, percent-of-total,
                  cost-plus, usage, or custom formula.
                - Use only supplied list price, quantity, cost, configuration, and rule context.
                - Explain method selection and calculation inputs before discount agents run.

                Enforcement:
                - Standard configured methods may be marked automatic.
                - Missing method configuration, custom formula ambiguity, or out-of-policy method
                  changes must be flagged REQUIRES_REVIEW.
                - Include method, inputs, calculation basis, dependencies, and audit-relevant
                  assumptions in the JSON response.
                """;
    }
}
