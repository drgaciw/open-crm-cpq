package com.opencrm.gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@code PUT /api/v1/price-books/**} through the gateway {@code RequestRateLimiter} filter.
 * The limiter is an in-memory stub with a burst of one: the first call is forwarded, the second is
 * HTTP 429. Production replenish and burst stay 50 and 100; Redis is not required.
 */
@Tag("unit")
class WriteRouteRateLimit429Test {

    @Test
    void secondPriceBookPutIs429() throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        upstream.start();
        try {
            PriceBookRoute.upstreamUri = "http://127.0.0.1:" + upstream.getAddress().getPort();
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(
                            SslAutoConfiguration.class, WebFluxAutoConfiguration.class, GatewayAutoConfiguration.class))
                    .withUserConfiguration(PriceBookRoute.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var gateway = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                                .handle(new ReactorHttpHandlerAdapter(
                                        WebHttpHandlerBuilder.applicationContext(context).build()))
                                .bindNow();
                        try {
                            WebTestClient client = WebTestClient.bindToServer()
                                    .baseUrl("http://127.0.0.1:" + gateway.port())
                                    .responseTimeout(Duration.ofSeconds(10))
                                    .build();
                            List<Integer> statuses = new ArrayList<>();
                            for (int attempt = 0; attempt < 2; attempt++) {
                                statuses.add(client.put().uri("/api/v1/price-books/42")
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer rate-limit-probe")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .bodyValue("{\"name\":\"Renamed\"}")
                                        .exchange()
                                        .returnResult(String.class)
                                        .getStatus()
                                        .value());
                            }
                            assertThat(statuses.get(0)).isEqualTo(HttpStatus.OK.value());
                            assertThat(statuses.get(1)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
                        } finally {
                            gateway.disposeNow();
                        }
                    });
        } finally {
            upstream.stop(0);
        }
    }

    @Configuration
    static class PriceBookRoute {
        static String upstreamUri;

        @Bean
        BurstOneLimiter burstOneLimiter() {
            return new BurstOneLimiter();
        }

        @Bean
        RouteLocator priceBookWrites(RouteLocatorBuilder builder, BurstOneLimiter limiter) {
            KeyResolver keys = exchange -> Mono.just("client");
            var config = new RequestRateLimiterGatewayFilterFactory.Config();
            config.setRateLimiter(limiter);
            config.setKeyResolver(keys);
            config.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            var filter = new RequestRateLimiterGatewayFilterFactory(limiter, keys).apply(config);
            return builder.routes()
                    .route("pricebook-service", spec -> spec.path("/api/v1/price-books/**")
                            .filters(gateway -> gateway.filter(filter))
                            .uri(upstreamUri))
                    .build();
        }
    }

    /** Allows one call, then refuses. Stands in for Redis burst capacity 1. */
    static final class BurstOneLimiter implements RateLimiter<Object> {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Mono<Response> isAllowed(String routeId, String id) {
            boolean allowed = calls.incrementAndGet() == 1;
            return Mono.just(new Response(allowed, Map.of()));
        }

        @Override
        public Map<String, Object> getConfig() {
            return Map.of();
        }

        @Override
        public Class<Object> getConfigClass() {
            return Object.class;
        }

        @Override
        public Object newConfig() {
            return new Object();
        }
    }
}
