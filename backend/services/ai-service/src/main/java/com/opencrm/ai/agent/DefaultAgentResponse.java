package com.opencrm.ai.agent;

import java.util.Map;

/**
 * Default serializable implementation of {@link AgentResponse}.
 * All fields are public for Jackson serialization without extra config.
 */
public record DefaultAgentResponse(
        boolean success,
        String output,
        Map<String, Object> metadata
) implements AgentResponse {

    /** Factory: successful LLM response. */
    public static DefaultAgentResponse success(String output, Map<String, Object> metadata) {
        return new DefaultAgentResponse(true, output, metadata != null ? metadata : Map.of());
    }

    /** Factory: error response (LLM call failed or guardrail triggered). */
    public static DefaultAgentResponse failure(String reason) {
        return new DefaultAgentResponse(false, reason, Map.of("error", true));
    }

    @Override
    public boolean isSuccess() { return success; }

    @Override
    public String getOutput() { return output; }

    @Override
    public Map<String, Object> getMetadata() { return metadata; }
}
