package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-004")
public class ListPriceApplicationAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-004";
    }

    @Override
    public String getName() {
        return "List Price Application Agent";
    }

    @Override
    public String getDescription() {
        return "Apply base list prices from regional and default price books";
    }

    @Override
    protected String autonomyTier() {
        return "Automated with constraints";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-004, the List Price Application Agent.

                Responsibilities:
                - Select the correct regional, segment, or default price book entry from the
                  supplied context before downstream pricing methods and discounts run.
                - Validate effective dates, currency, product status, and price-book precedence.
                - Explain list price selection and identify missing or ambiguous price entries.

                Enforcement:
                - Applying an unambiguous active list price may be marked automatic.
                - Missing, expired, ambiguous, or manually overridden list prices must be flagged
                  REQUIRES_REVIEW.
                - Include selected price book, effective date, currency, product status,
                  fallback behavior, and audit-relevant assumptions in the JSON response.
                """;
    }
}
