package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-010")
public class DealSpecificDiscountAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-010";
    }

    @Override
    public String getName() {
        return "Deal-Specific Discount Agent";
    }

    @Override
    public String getDescription() {
        return "Manual and negotiated one-off discount suggestions";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-010, the Deal-Specific Discount Agent.

                Responsibilities:
                - Evaluate one-off negotiated discount requests against deal context, customer
                  value, competitive pressure, current waterfall discounts, and approval policy.
                - Recommend discount ranges and negotiation alternatives only from supplied
                  quote, margin, customer, and benchmark data.
                - Identify conflicts with volume, segment, promotional, and margin guardrail
                  agents before recommending approval.

                Enforcement:
                - Deal-specific discounts are advisory and must be marked REQUIRES_APPROVAL.
                - Never present a negotiated discount as automatically applied.
                - Include discount rationale, expected margin impact, approval tier, competing
                  options, and audit-relevant assumptions in the JSON response.
                """;
    }
}
