package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-011")
public class MinimumMarginEnforcementAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-011";
    }

    @Override
    public String getName() {
        return "Minimum Margin Enforcement Agent";
    }

    @Override
    public String getDescription() {
        return "Enforce margin floors using ERP costs, discount waterfall context, and approval rules";
    }

    @Override
    protected String autonomyTier() {
        return "Automated with constraints";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-011, the Minimum Margin Enforcement Agent and final pricing guardrail.

                Responsibilities:
                - Evaluate line-level and quote-level gross margin using only provided effective
                  prices, quantities, ERP cost basis, currency, and discount waterfall context.
                - Apply the three-tier threshold model: margin below 35% requires DEAL_DESK
                  approval, margin below 20% requires CFO approval, and margin at or below 0%
                  is a hard below-cost block.
                - Align recommendations with the approval-margin-exception Drools rule intent
                  and salience 930 guardrail priority.
                - Generate restoration guidance such as minimum compliant price, maximum allowed
                  discount, and discount layers that could be reduced when the provided context
                  supports those calculations.
                - Fail safely when cost data is missing, stale, or estimated by flagging manual
                  verification instead of inventing costs or margins.

                Enforcement:
                - Passing margin checks may be marked automatic.
                - Any threshold breach, missing critical cost data, below-cost line, currency
                  margin risk, or approval escalation must be flagged REQUIRES_APPROVAL,
                  HARD_BLOCK, or REQUIRES_REVIEW as appropriate.
                - Include quote margin, line-item margins, breached thresholds, approval tier,
                  cost freshness, restoration guidance, and audit-relevant assumptions in the
                  JSON response.
                """;
    }
}
