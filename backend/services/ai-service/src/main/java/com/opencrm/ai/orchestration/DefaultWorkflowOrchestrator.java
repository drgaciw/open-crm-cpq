package com.opencrm.ai.orchestration;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import com.opencrm.ai.agent.DefaultAgentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DefaultWorkflowOrchestrator implements WorkflowOrchestrator {

    private final Map<String, CpqAgent> agentRegistry;
    private final boolean enabled;

    public DefaultWorkflowOrchestrator(
            List<CpqAgent> agents,
            @Value("${opencrm.ai.agents.enabled:false}") boolean enabled
    ) {
        this.agentRegistry = agents.stream()
                .collect(java.util.stream.Collectors.toMap(CpqAgent::getAgentId, java.util.function.Function.identity()));
        this.enabled = enabled;
    }

    @Override
    public AgentResponse invoke(String workflowId, AgentRequest request) {
        if (!enabled) {
            return DefaultAgentResponse.failure(disabledMessage(workflowId));
        }
        if ("deal-desk".equalsIgnoreCase(workflowId) || "deal-desk-workflow".equalsIgnoreCase(workflowId)) {
            CpqAgent dealDesk = Optional.ofNullable(agentRegistry.get("cpq-agent-037"))
                    .orElseThrow(() -> new IllegalArgumentException("Deal desk agent not registered: cpq-agent-037"));
            return dealDesk.invoke(request);
        }
        return DefaultAgentResponse.success("Workflow stub executed for " + workflowId, Map.of("workflowId", workflowId));
    }

    @Override
    public Flux<String> stream(String workflowId, AgentRequest request) {
        if (!enabled) {
            return Flux.just(disabledMessage(workflowId));
        }
        if ("deal-desk".equalsIgnoreCase(workflowId) || "deal-desk-workflow".equalsIgnoreCase(workflowId)) {
            CpqAgent dealDesk = Optional.ofNullable(agentRegistry.get("cpq-agent-037"))
                    .orElseThrow(() -> new IllegalArgumentException("Deal desk agent not registered: cpq-agent-037"));
            return dealDesk.stream(request);
        }
        return Flux.just("Workflow stub stream for " + workflowId);
    }

    private String disabledMessage(String workflowId) {
        return "AI orchestration feature is disabled for " + workflowId + ".";
    }
}
