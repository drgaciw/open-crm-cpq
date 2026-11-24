package com.opencrm.gateway.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@code PUT /api/v1/price-books/**} through the gateway and expects the second call to be
 * HTTP 429. The route in this revision has no limiter yet, so the assertion fails on 200.
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
        RouteLocator priceBookWrites(RouteLocatorBuilder builder) {
            return builder.routes()
                    .route("pricebook-service", spec -> spec.path("/api/v1/price-books/**").uri(upstreamUri))
                    .build();
        }
    }
}
