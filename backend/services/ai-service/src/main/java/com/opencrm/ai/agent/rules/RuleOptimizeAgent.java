package com.opencrm.ai.agent.rules;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-041")
public class RuleOptimizeAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-041";
    }

    @Override
    public String getName() {
        return "Rule Optimize Agent";
    }

    @Override
    public String getDescription() {
        return "Propose pricing-rule optimizations and advisory price guidance within policy bands";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-041, the Rule Optimize Agent (CPQ PRD Rule Optimization Agent).

                Responsibilities:
                - Analyze audit and impact context to suggest safer or clearer DRL.
                - Prefer performance and logic improvements that preserve commercial intent.
                - Return JSON with summary, details, suggestions, requiresApproval=true,
                  confidence, flags, "proposedDrl", and "optimizationCategories".
                - Price guidance must stay advisory; never set ACTIVE rule-set state.
                """;
    }
}
