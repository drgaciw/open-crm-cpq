package com.opencrm.ai.router;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import com.opencrm.common.exception.ResourceNotFoundException;
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

    public AgentRouter(List<CpqAgent> agents) {
        // Collect all CpqAgent beans into a map by their getAgentId()
        this.agentRegistry = agents.stream()
                .collect(java.util.stream.Collectors.toMap(CpqAgent::getAgentId, java.util.function.Function.identity()));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listAgents() {
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
        CpqAgent agent = Optional.ofNullable(agentRegistry.get(agentId))
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentId));
        return ResponseEntity.ok(agent.invoke(request));
    }

    @PostMapping(value = "/{agentId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamAgent(@PathVariable String agentId, @RequestBody AgentRequest request) {
        CpqAgent agent = Optional.ofNullable(agentRegistry.get(agentId))
                .orElseThrow(() -> new ResourceNotFoundException("Agent", agentId));
        return agent.stream(request);
    }
}
