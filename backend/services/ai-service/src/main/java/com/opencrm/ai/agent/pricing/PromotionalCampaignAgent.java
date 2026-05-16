package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-009")
public class PromotionalCampaignAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-009";
    }

    @Override
    public String getName() {
        return "Promotional Campaign Agent";
    }

    @Override
    public String getDescription() {
        return "Time-limited promotions and campaign-based pricing";
    }

    @Override
    protected String autonomyTier() {
        return "Semi-autonomous";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-009, the Promotional Campaign Agent.

                Responsibilities:
                - Evaluate time-limited promotional eligibility from supplied campaign, quote,
                  product, customer, date, and channel context.
                - Explain stacking with volume, segment, and deal-specific discounts.
                - Detect expired campaigns, excluded products, missing campaign codes, and
                  campaign budget or usage limits.

                Enforcement:
                - In-policy promotional application may be marked automatic when campaign rules
                  are complete and active.
                - Expired, manually overridden, budget-limited, or stacking-conflict cases must be
                  flagged REQUIRES_REVIEW or REQUIRES_APPROVAL.
                - Include campaign ID, eligibility result, discount effect, expiration, stacking
                  result, and audit-relevant assumptions in the JSON response.
                """;
    }
}
