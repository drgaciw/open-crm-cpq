package com.opencrm.ai.registry;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Fail-safe structured result for CPQ agent tool calls. Never throws to the LLM layer.
 */
public record CpqToolResponse(
        boolean success,
        String message,
        String errorCode,
        JsonNode data
) {

    public static CpqToolResponse ok(JsonNode data, String message) {
        return new CpqToolResponse(true, message, null, data);
    }

    public static CpqToolResponse gatewayUnavailable(String detail) {
        return new CpqToolResponse(
                false,
                "CPQ gateway unreachable: " + detail,
                "GATEWAY_UNAVAILABLE",
                null);
    }

    public static CpqToolResponse clientError(String message) {
        return new CpqToolResponse(false, message, "CPQ_CLIENT_ERROR", null);
    }
}
