package com.opencrm.ai.selection.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.ai.selection.SelectionTypes.Usage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ExecutionTypes {
    private ExecutionTypes() { }

    /** Only client-side function definitions are supported; this layer never invokes tools. */
    public record Request(String system, String user, JsonNode responseFormat, List<JsonNode> tools,
            boolean containsToolResults) {
        public Request {
            system = system == null ? "" : system;
            user = user == null ? "" : user;
            tools = tools == null ? List.of() : tools.stream().<JsonNode>map(JsonNode::deepCopy).toList();
            responseFormat = responseFormat == null ? null : responseFormat.deepCopy();
            if (responseFormat != null && !List.of("json_object", "json_schema")
                    .contains(responseFormat.path("type").asText())) throw new IllegalArgumentException("Unsupported response format");
            if (responseFormat != null && "json_schema".equals(responseFormat.path("type").asText()))
                JsonSchemaSubset.checkSchema(responseFormat.path("json_schema").path("schema"));
            for (JsonNode tool : tools) {
                if (!"function".equals(tool.path("type").asText()) || !tool.path("function").isObject()
                        || tool.path("function").path("name").asText().isBlank())
                    throw new IllegalArgumentException("Only named client function tools are allowed");
                JsonSchemaSubset.checkSchema(tool.path("function").path("parameters"));
            }
        }
    }

    public record Completion(String content, List<JsonNode> toolCalls, Usage usage, String model,
            String provider) {
        public Completion { toolCalls = List.copyOf(toolCalls); }
    }

    /** No prompts, outputs, headers, credentials, or provider error bodies are recorded. */
    public record Audit(Instant at, String profileId, String policyVersion, String endpointKey,
            int attempt, long latencyMillis, BigDecimal reservedCost, BigDecimal actualCost,
            Usage usage, String outcome) { }

    public static final class Unavailable extends RuntimeException {
        private final String reason;
        public Unavailable(String reason) { super(reason); this.reason = reason; }
        public String reason() { return reason; }
    }

    public static final class TransportFailure extends Exception {
        private final boolean retryable;
        public TransportFailure(String reason, boolean retryable) { super(reason); this.retryable = retryable; }
        public boolean retryable() { return retryable; }
    }
}
