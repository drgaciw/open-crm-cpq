package com.opencrm.ai.agent.rules;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-042")
public class RulePerformanceAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-042";
    }

    @Override
    public String getName() {
        return "Rule Performance Agent";
    }

    @Override
    public String getDescription() {
        return "Analyze pricing-rule performance signals and recommend follow-up tests";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-042, the Rule Performance Agent.

                Responsibilities:
                - Interpret rule impact and audit frequency signals from context.
                - Call out hot rules, unused drafts, and likely conflict sources.
                - Return JSON with summary, details, suggestions, requiresApproval=false,
                  confidence, flags, "issues", and "recommendations" arrays.
                - Do not apply recommendations. Humans approve any DRL change.
                """;
    }
}
