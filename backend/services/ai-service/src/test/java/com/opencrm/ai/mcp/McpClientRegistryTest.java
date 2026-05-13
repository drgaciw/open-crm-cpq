package com.opencrm.ai.mcp;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientRegistryTest {

    @Test
    void defaultClientsRemainDisabled() {
        McpClientRegistry registry = new McpClientRegistry(new McpClientProperties());

        assertTrue(registry.enabledClients().isEmpty());
    }

    @Test
    void enabledClientsExposeConfiguredDescriptors() {
        McpClientProperties properties = new McpClientProperties();
        Map<String, McpClientProperties.Client> clients = new LinkedHashMap<>();
        clients.put("sequential-thinking", new McpClientProperties.Client(true, URI.create("http://localhost:3101")));
        clients.put("context7", new McpClientProperties.Client(true, URI.create("http://localhost:3102")));
        clients.put("puppeteer", new McpClientProperties.Client(false, URI.create("http://localhost:3103")));
        properties.setClients(clients);

        List<McpClientDescriptor> enabledClients = new McpClientRegistry(properties).enabledClients();

        assertEquals(List.of(
                new McpClientDescriptor("sequential-thinking", URI.create("http://localhost:3101")),
                new McpClientDescriptor("context7", URI.create("http://localhost:3102"))
        ), enabledClients);
    }
}
