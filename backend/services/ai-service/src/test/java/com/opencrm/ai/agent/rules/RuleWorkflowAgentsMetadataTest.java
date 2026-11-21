package com.opencrm.ai.agent.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleWorkflowAgentsMetadataTest {

    @Test
    void ruleModifyAgentId() {
        assertEquals("cpq-agent-039", new RuleModifyAgent().getAgentId());
    }

    @Test
    void ruleSetEvaluateAgentId() {
        assertEquals("cpq-agent-040", new RuleSetEvaluateAgent().getAgentId());
    }

    @Test
    void ruleOptimizeAgentId() {
        assertEquals("cpq-agent-041", new RuleOptimizeAgent().getAgentId());
    }

    @Test
    void rulePerformanceAgentId() {
        assertEquals("cpq-agent-042", new RulePerformanceAgent().getAgentId());
    }
}
