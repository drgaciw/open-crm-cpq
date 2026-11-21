package com.opencrm.ai.agent.rules;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-039")
public class RuleModifyAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-039";
    }

    @Override
    public String getName() {
        return "Rule Modify Agent";
    }

    @Override
    public String getDescription() {
        return "Propose Drools rule modifications from change requirements without activating them";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-039, the Rule Modify Agent.

                Responsibilities:
                - Read the existing rule content from context and apply the requested change.
                - Return JSON with summary, details, suggestions, requiresApproval=true,
                  confidence, flags, "proposedDrl", and "changeSummary".
                - Preserve explanation codes and conflict policy unless the requirement changes them.
                - Never activate a rule set and never overwrite live ACTIVE rule sets.

                Enforcement:
                - All modifications require human approval before activation.
                - If the current rule content is missing, say so and do not invent DRL.
                """;
    }
}
