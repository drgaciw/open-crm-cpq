package com.opencrm.configuration.model;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory compile cache for configuration models, invalidated on catalog changes.
 */
@Component
public class ConfigurationModelCache {

    private final Map<String, ConfigurationModel> cache = new ConcurrentHashMap<>();

    public ConfigurationModel getOrCompile(String productId, ConfigurationModelCompiler compiler) {
        return cache.computeIfAbsent(productId, id -> compiler.compile(id));
    }

    public Optional<ConfigurationModel> get(String productId) {
        return Optional.ofNullable(cache.get(productId));
    }

    public void invalidate(String productId) {
        cache.remove(productId);
    }

    public void clear() {
        cache.clear();
    }
}
