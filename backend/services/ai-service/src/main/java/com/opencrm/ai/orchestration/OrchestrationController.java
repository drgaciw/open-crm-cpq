package com.opencrm.ai.orchestration;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.DefaultAgentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/orchestrations")
public class OrchestrationController {

    private final WorkflowOrchestrator orchestrator;
    private final boolean enabled;

    public OrchestrationController(
            WorkflowOrchestrator orchestrator,
            @Value("${opencrm.ai.agents.enabled:false}") boolean enabled
    ) {
        this.orchestrator = orchestrator;
        this.enabled = enabled;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listWorkflows() {
        if (!enabled) {
            return ResponseEntity.ok(List.of());
        }
        List<Map<String, String>> workflows = List.of(
                Map.of("id", "deal-desk", "name", "Deal Desk Workflow", "description", "Coordinates Deal Strategist, Constitutional Guardian, Commercial Architect, Deal Desk Orchestrator"),
                Map.of("id", "deal-desk-workflow", "name", "Deal Desk Workflow", "description", "Coordinates the four orchestration agents for deal-desk")
        );
        return ResponseEntity.ok(workflows);
    }

    @PostMapping("/{workflowId}/invoke")
    public ResponseEntity<AgentResponse> invokeWorkflow(@PathVariable String workflowId, @RequestBody AgentRequest request) {
        if (!enabled) {
            return ResponseEntity.status(503).body(DefaultAgentResponse.failure(disabledMessage(workflowId)));
        }
        return ResponseEntity.ok(orchestrator.invoke(workflowId, request));
    }

    @PostMapping(value = "/{workflowId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamWorkflow(@PathVariable String workflowId, @RequestBody AgentRequest request) {
        if (!enabled) {
            return Flux.just(disabledMessage(workflowId));
        }
        return orchestrator.stream(workflowId, request);
    }

    private String disabledMessage(String workflowId) {
        return "AI orchestration feature is disabled for " + workflowId + ".";
    }
}
