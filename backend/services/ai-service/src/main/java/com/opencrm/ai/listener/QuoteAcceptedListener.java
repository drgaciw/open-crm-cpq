package com.opencrm.ai.listener;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.DefaultAgentRequest;
import com.opencrm.ai.orchestration.WorkflowOrchestrator;

import java.util.List;
import java.util.Map;

public class QuoteAcceptedListener {

    private final WorkflowOrchestrator orchestrator;

    public QuoteAcceptedListener(WorkflowOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    public void handleQuoteAccepted(String message) {
        AgentRequest request = new DefaultAgentRequest(null, List.of(), Map.of("event", "quote:accepted", "payload", message != null ? message : ""));
        orchestrator.invoke("deal-desk", request);
    }
}
