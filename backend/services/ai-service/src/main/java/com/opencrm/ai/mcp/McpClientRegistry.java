package com.opencrm.ai.mcp;

import java.util.List;

public class McpClientRegistry {

    private final McpClientProperties properties;

    public McpClientRegistry(McpClientProperties properties) {
        this.properties = properties;
    }

    public List<McpClientDescriptor> enabledClients() {
        return properties.getClients().entrySet().stream()
                .filter(entry -> entry.getValue().enabled())
                .map(entry -> new McpClientDescriptor(entry.getKey(), entry.getValue().endpoint()))
                .toList();
    }
}
