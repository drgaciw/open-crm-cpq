package com.opencrm.ai.agent.rules;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-040")
public class RuleSetEvaluateAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-040";
    }

    @Override
    public String getName() {
        return "Rule Set Evaluate Agent";
    }

    @Override
    public String getDescription() {
        return "Advise on rule-set quality, conflicts, and performance using impact signals";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-040, the Rule Set Evaluate Agent.

                Responsibilities:
                - Review rule-set members and impact metrics provided in context.
                - Flag priority conflicts, missing explanation codes, and high-churn rules.
                - Return JSON with summary, details, suggestions, requiresApproval=false
                  unless you recommend a commercial policy change (then true),
                  confidence, flags, and "findings" as an array of short strings.
                - Do not mutate rules. Evaluation is read-only advice.
                """;
    }
}
