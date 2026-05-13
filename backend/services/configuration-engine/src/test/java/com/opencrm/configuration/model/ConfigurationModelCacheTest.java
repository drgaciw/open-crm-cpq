package com.opencrm.configuration.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Configuration model cache")
class ConfigurationModelCacheTest {

    private ConfigurationModelCompiler compiler;
    private ConfigurationModelCache cache;

    @BeforeEach
    void setUp() {
        compiler = mock(ConfigurationModelCompiler.class);
        cache = new ConfigurationModelCache(compiler);
    }

    @Test
    @DisplayName("should compile once and reuse cached model")
    void cachesCompiledModelByProduct() {
        ConfigurationModel model = model(10L, Set.of(10L));
        when(compiler.compile(10L)).thenReturn(model);

        ConfigurationModel first = cache.getOrCompile(10L);
        ConfigurationModel second = cache.getOrCompile(10L);

        assertSame(model, first);
        assertSame(first, second);
        verify(compiler, times(1)).compile(10L);
    }

    @Test
    @DisplayName("should recompile after direct product invalidation")
    void invalidatesDirectProduct() {
        ConfigurationModel first = model(10L, Set.of(10L));
        ConfigurationModel second = model(10L, Set.of(10L));
        when(compiler.compile(10L)).thenReturn(first, second);

        cache.getOrCompile(10L);
        cache.invalidateProduct(10L);
        ConfigurationModel result = cache.getOrCompile(10L);

        assertSame(second, result);
        verify(compiler, times(2)).compile(10L);
    }

    @Test
    @DisplayName("should invalidate parent models when a referenced bundle component changes")
    void invalidatesModelsReferencingChangedProduct() {
        ConfigurationModel parentModel = model(20L, Set.of(20L, 30L));
        ConfigurationModel parentModelAfterInvalidation = model(20L, Set.of(20L, 30L));
        when(compiler.compile(20L)).thenReturn(parentModel, parentModelAfterInvalidation);

        cache.getOrCompile(20L);
        cache.invalidateProduct(30L);
        ConfigurationModel result = cache.getOrCompile(20L);

        assertSame(parentModelAfterInvalidation, result);
        verify(compiler, times(2)).compile(20L);
    }

    private ConfigurationModel model(Long productId, Set<Long> referencedProductIds) {
        return new ConfigurationModel(
                productId,
                "v-" + System.nanoTime(),
                List.of(),
                List.of(),
                referencedProductIds,
                Instant.now()
        );
    }
}
