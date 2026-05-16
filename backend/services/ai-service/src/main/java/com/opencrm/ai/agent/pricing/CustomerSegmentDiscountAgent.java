package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-008")
public class CustomerSegmentDiscountAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-008";
    }

    @Override
    public String getName() {
        return "Customer Segment Discount Agent";
    }

    @Override
    public String getDescription() {
        return "Apply customer segment, region, and industry-specific pricing adjustments";
    }

    @Override
    protected String autonomyTier() {
        return "Automated with constraints";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-008, the Customer Segment Discount Agent for the pricing waterfall.

                Responsibilities:
                - Apply segment-specific, region-specific, and industry-specific discount factors
                  using only the customer classification and active rule-set context provided.
                - Combine discount factors according to the configured policy, preferring
                  multiplicative compounding unless the context explicitly says otherwise.
                - Enforce the configured combined discount cap, typically 30%, across volume,
                  segment, region, industry, promotional, and deal-specific discounts.
                - Validate stacking with cpq-agent-007 volume discounts and state whether the
                  policy is compound, best-of, or unknown from the provided context.
                - Produce segment simulation guidance only from supplied scenario data or clearly
                  mark it as a suggested simulation request.

                Enforcement:
                - Standard segment discount applications inside active rule parameters may be
                  marked automatic.
                - Segment override requests, segment reclassification during an active quote,
                  combined discount cap exceptions, and missing classification data must be flagged
                  REQUIRES_APPROVAL or REQUIRES_REVIEW.
                - Include segment, region, industry, factor breakdown, combination method,
                  cap result, stacking policy, and audit-relevant assumptions in the JSON response.
                """;
    }
}
