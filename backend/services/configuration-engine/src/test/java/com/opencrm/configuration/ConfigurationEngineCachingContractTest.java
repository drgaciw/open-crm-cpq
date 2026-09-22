package com.opencrm.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.util.ClassUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full UAT 2026-09-19: configuration-engine could not boot —
 * {@code A component required a bean of type 'org.springframework.cache.CacheManager'}.
 *
 * <p>{@code @EnableCaching} makes Spring's cache interceptor demand a {@code CacheManager}.
 * Under Boot 4's modularised auto-configuration one only exists when the cache module
 * ({@code spring-boot-starter-cache}) is on the classpath, which this service never declared.
 * Nothing here uses Spring's cache abstraction — the service keeps its own TTL maps — so the
 * annotation had no effect except to stop the application starting.
 *
 * <p>The invariant, which holds for either remedy: caching may only be switched on when a
 * {@code CacheManager} can actually be auto-configured.
 */
@DisplayName("configuration-engine caching contract")
class ConfigurationEngineCachingContractTest {

    private static final String CACHE_AUTO_CONFIGURATION =
            "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration";

    @Test
    @DisplayName("@EnableCaching is only present when a CacheManager can be auto-configured")
    void enableCachingRequiresCacheAutoConfiguration() {
        boolean cachingEnabled = ConfigurationEngineApplication.class.isAnnotationPresent(EnableCaching.class);
        boolean cacheManagerAvailable =
                ClassUtils.isPresent(CACHE_AUTO_CONFIGURATION, getClass().getClassLoader());

        assertThat(!cachingEnabled || cacheManagerAvailable)
                .as("@EnableCaching is declared but spring-boot-starter-cache is not on the classpath: "
                        + "no CacheManager bean can exist and the service fails to start")
                .isTrue();
    }
}
