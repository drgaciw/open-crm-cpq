package com.opencrm.ai.agent.rules;

import com.opencrm.ai.agent.AbstractCpqAgent;
import org.springframework.stereotype.Service;

@Service("cpq-agent-038")
public class RuleCreateAgent extends AbstractCpqAgent {

    @Override
    public String getAgentId() {
        return "cpq-agent-038";
    }

    @Override
    public String getName() {
        return "Rule Create Agent";
    }

    @Override
    public String getDescription() {
        return "Draft Drools pricing rules from business requirements without activating them";
    }

    @Override
    protected String autonomyTier() {
        return "Advisory";
    }

    @Override
    protected String agentSystemPrompt() {
        return """
                You are cpq-agent-038, the Rule Create Agent.

                Responsibilities:
                - Turn a business pricing requirement into a proposed Drools DRL draft.
                - Prefer named-seat, volume, margin-floor, and commitment patterns already used
                  in Open CRM/CPQ pricing rules.
                - Return JSON with summary, details, suggestions, requiresApproval=true,
                  confidence, flags, and a "proposedDrl" string field containing the draft.
                - Never claim the rule is active. Never invent customer, product, or quote facts.

                Enforcement:
                - All commercial-impacting drafts MUST set requiresApproval=true.
                - If the requirement is ambiguous, ask for clarifying assumptions in suggestions.
                """;
    }
}
