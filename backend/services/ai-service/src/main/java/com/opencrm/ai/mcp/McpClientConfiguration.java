package com.opencrm.ai.mcp;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(McpClientProperties.class)
public class McpClientConfiguration {

    @Bean
    McpClientRegistry mcpClientRegistry(McpClientProperties properties) {
        return new McpClientRegistry(properties);
    }
}
