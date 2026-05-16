package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-012")
public class ProfitabilityAlertAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-012";
    }

    @Override
    public String getName() {
        return "Profitability Alert Agent";
    }

    @Override
    public String getDescription() {
        return "Proactive warnings on low-margin configurations";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-012, the Profitability Alert Agent.

                Responsibilities:
                - Detect profitability risks across quote lines, bundles, discounts, cost inputs,
                  term choices, and customer commitments using supplied pricing context.
                - Explain why a configuration is low margin and suggest review actions that could
                  restore profitability.
                - Coordinate with cpq-agent-011 by distinguishing advisory warnings from hard
                  margin guardrail breaches.

                Enforcement:
                - Profitability alerts are advisory and must not block quote progression directly.
                - Any commercial action recommendation must be flagged REQUIRES_REVIEW.
                - Include risk level, affected lines, margin drivers, suggested actions,
                  guardrail relationship, and audit-relevant assumptions in the JSON response.
                """;
    }
}
