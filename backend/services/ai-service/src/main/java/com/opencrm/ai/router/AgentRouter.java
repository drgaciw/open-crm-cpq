package com.opencrm.ai.router;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import com.opencrm.ai.agent.DefaultAgentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/ai/agents")
public class AgentRouter {

    private final Map<String, CpqAgent> agentRegistry;
    private final boolean agentsEnabled;

    public AgentRouter(
            List<CpqAgent> agents,
            @Value("${opencrm.ai.agents.enabled:false}") boolean agentsEnabled
    ) {
        // Collect all CpqAgent beans into a map by their getAgentId()
        this.agentRegistry = agents.stream()
                .collect(java.util.stream.Collectors.toMap(CpqAgent::getAgentId, java.util.function.Function.identity()));
        this.agentsEnabled = agentsEnabled;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listAgents() {
        if (!agentsEnabled) {
            return ResponseEntity.ok(List.of());
        }

        List<Map<String, String>> agents = agentRegistry.values().stream()
                .map(agent -> Map.of(
                        "id", agent.getAgentId(),
                        "name", agent.getName(),
                        "description", agent.getDescription()
                ))
                .toList();
        return ResponseEntity.ok(agents);
    }

    @PostMapping("/{agentId}/invoke")
    public ResponseEntity<AgentResponse> invokeAgent(@PathVariable String agentId, @RequestBody AgentRequest request) {
        if (!agentsEnabled) {
            return ResponseEntity.status(503).body(disabledResponse(agentId));
        }

        CpqAgent agent = Optional.ofNullable(agentRegistry.get(agentId))
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        return ResponseEntity.ok(agent.invoke(request));
    }

    @PostMapping(value = "/{agentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamAgent(@PathVariable String agentId, @RequestBody AgentRequest request) {
        if (!agentsEnabled) {
            return Flux.just(disabledMessage(agentId));
        }

        CpqAgent agent = Optional.ofNullable(agentRegistry.get(agentId))
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        return agent.stream(request);
    }

    private AgentResponse disabledResponse(String agentId) {
        return DefaultAgentResponse.failure(disabledMessage(agentId));
    }

    private String disabledMessage(String agentId) {
        return "AI agent feature is disabled for " + agentId + ".";
    }
}
