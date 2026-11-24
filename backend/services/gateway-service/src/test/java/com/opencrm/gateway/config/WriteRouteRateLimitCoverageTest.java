package com.opencrm.gateway.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The gateway default {@code RequestRateLimiter} is the limiter for the write routes.
 * This does not call Redis and does not assert a live 429. It records that the default filter
 * (50 replenish / 100 burst / 1 token) is on the document that every profile inherits, and that
 * the dev profile replaces routes without replacing {@code default-filters}.
 */
@Tag("unit")
class WriteRouteRateLimitCoverageTest {

    private static final String[] WRITE_ROUTE_PREFIXES = {
            "/api/v1/quotes/**",
            "/api/v1/product-bundles/**",
            "/api/v1/configuration/**",
            "/api/v1/price-books/**"
    };

    @Test
    void gatewayDefaultRequestRateLimiterCoversTheWriteRoutes() throws Exception {
        String yaml;
        try (var in = WriteRouteRateLimitCoverageTest.class.getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(in).as("gateway application.yml").isNotNull();
            yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String[] documents = yaml.split("\\n---\\n");
        assertThat(documents.length).isGreaterThanOrEqualTo(2);
        String defaults = documents[0];
        String dev = documents[1];

        assertThat(defaults).contains("name: RequestRateLimiter");
        assertThat(defaults).contains("redis-rate-limiter.replenishRate: ${RATE_LIMIT_REPLENISH_RATE:50}");
        assertThat(defaults).contains("redis-rate-limiter.burstCapacity: ${RATE_LIMIT_BURST_CAPACITY:100}");
        assertThat(defaults).contains("redis-rate-limiter.requestedTokens: ${RATE_LIMIT_REQUESTED_TOKENS:1}");
        assertThat(dev).doesNotContain("default-filters");
        for (String path : WRITE_ROUTE_PREFIXES) {
            assertThat(defaults).contains(path);
            assertThat(dev).contains(path);
        }
    }
}
