package com.opencrm.ai.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "opencrm.ai.mcp")
public class McpClientProperties {

    private Map<String, Client> clients = defaultClients();

    public Map<String, Client> getClients() {
        return clients;
    }

    public void setClients(Map<String, Client> clients) {
        this.clients = clients;
    }

    private static Map<String, Client> defaultClients() {
        Map<String, Client> defaults = new LinkedHashMap<>();
        defaults.put("sequential-thinking", new Client(false, URI.create("http://localhost:3101")));
        defaults.put("context7", new Client(false, URI.create("http://localhost:3102")));
        defaults.put("puppeteer", new Client(false, URI.create("http://localhost:3103")));
        return defaults;
    }

    public record Client(boolean enabled, URI endpoint) {
    }
}
