package com.opencrm.configuration.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Configuration Model Cache")
class ConfigurationModelCacheTest {

    private ConfigurationModelCache cache;
    private ConfigurationModelCompiler compiler;

    @BeforeEach
    void setUp() {
        cache = new ConfigurationModelCache();
        compiler = new ConfigurationModelCompiler();
    }

    @Test
    @DisplayName("should compile and cache configuration model per product")
    void cachesCompiledModel() {
        ConfigurationModel first = cache.getOrCompile("product-1", compiler);
        ConfigurationModel second = cache.getOrCompile("product-1", compiler);

        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("should invalidate cache entry when catalog changes")
    void invalidatesOnCatalogChange() {
        ConfigurationModel cached = cache.getOrCompile("product-1", compiler);

        cache.invalidate("product-1");

        assertThat(cache.get("product-1")).isEmpty();
        ConfigurationModel recompiled = cache.getOrCompile("product-1", compiler);
        assertThat(recompiled).isNotSameAs(cached);
    }
}
