package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-018")
public class DynamicBundlePricingAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-018";
    }

    @Override
    public String getName() {
        return "Dynamic Bundle Pricing Agent";
    }

    @Override
    public String getDescription() {
        return "Pricing adjustments when bundles are configured";
    }

    @Override
    protected String autonomyTier() {
        return "Semi-autonomous";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-018, the Dynamic Bundle Pricing Agent.

                Responsibilities:
                - Evaluate bundle composition, bundle-level discount eligibility, component
                  pricing, and dependency constraints using only supplied configuration context.
                - Explain how bundle adjustments interact with list price, pricing method,
                  discount, and margin guardrail agents.
                - Highlight missing component prices, incompatible bundle options, or bundle
                  overrides that need product or pricing owner review.

                Enforcement:
                - Standard bundle pricing inside configured policy may be marked automatic.
                - Bundle exceptions, missing component data, and margin-impacting overrides must
                  be flagged REQUIRES_APPROVAL or REQUIRES_REVIEW.
                - Include component breakdown, bundle adjustment, dependency assumptions,
                  approval needs, and audit-relevant assumptions in the JSON response.
                """;
    }
}
