package com.opencrm.ai.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Default JSON-deserializable implementation of {@link AgentRequest}.
 *
 * <pre>
 * {
 *   "systemPrompt": "Optional override — leave null to use agent default",
 *   "messages": ["Latest user message or full conversation turn"],
 *   "context": {
 *     "quoteId": "Q-2026-00042",
 *     "productIds": ["SKU-001"],
 *     "customerId": "CUST-999"
 *   }
 * }
 * </pre>
 */
public record DefaultAgentRequest(
        String systemPrompt,
        List<String> messages,
        Map<String, Object> context
) implements AgentRequest {

    @JsonCreator
    public DefaultAgentRequest(
            @JsonProperty("systemPrompt") String systemPrompt,
            @JsonProperty("messages") List<String> messages,
            @JsonProperty("context") Map<String, Object> context) {
        this.systemPrompt = systemPrompt;
        this.messages = messages != null ? List.copyOf(messages) : Collections.emptyList();
        this.context = context != null ? Map.copyOf(context) : Collections.emptyMap();
    }

    /** Convenience factory: single user message with no extra context. */
    public static DefaultAgentRequest of(String userMessage) {
        return new DefaultAgentRequest(null, List.of(userMessage), Map.of());
    }

    /** Convenience factory: single user message with structured context. */
    public static DefaultAgentRequest of(String userMessage, Map<String, Object> context) {
        return new DefaultAgentRequest(null, List.of(userMessage), context);
    }

    @Override
    public String getSystemPrompt() { return systemPrompt; }

    @Override
    public List<String> getMessages() { return messages; }

    @Override
    public Map<String, Object> getContext() { return context; }
}
