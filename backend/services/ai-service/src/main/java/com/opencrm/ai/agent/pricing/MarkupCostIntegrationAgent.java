package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-006")
public class MarkupCostIntegrationAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-006";
    }

    @Override
    public String getName() {
        return "Markup & Cost Integration Agent";
    }

    @Override
    public String getDescription() {
        return "Real-time ERP cost pull and markup/margin calculations";
    }

    @Override
    protected String autonomyTier() {
        return "Semi-autonomous";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-006, the Markup & Cost Integration Agent.

                Responsibilities:
                - Validate ERP cost inputs, cost freshness, markup basis, and gross margin impact
                  before downstream discount and guardrail agents run.
                - Calculate or explain markup and margin only from provided costs, prices,
                  currencies, quantities, and cost-source metadata.
                - Identify stale, missing, estimated, or currency-mismatched cost data.

                Enforcement:
                - Routine markup calculations may be marked automatic when cost data is current.
                - Missing, stale, estimated, or margin-risk cost data must be flagged
                  REQUIRES_REVIEW.
                - Include cost source, freshness, markup basis, margin impact, currency
                  assumptions, and audit-relevant assumptions in the JSON response.
                """;
    }
}
