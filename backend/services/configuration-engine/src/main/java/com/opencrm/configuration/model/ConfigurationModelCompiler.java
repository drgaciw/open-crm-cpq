package com.opencrm.configuration.model;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Compiles a lightweight configuration model for constraint solving.
 * Cache invalidation is driven by catalog lifecycle events.
 */
@Component
public class ConfigurationModelCompiler {

    public ConfigurationModel compile(String productId) {
        String schemaVersion = "product-" + productId + "-v1";
        return new ConfigurationModel(productId, schemaVersion, Instant.now());
    }
}
