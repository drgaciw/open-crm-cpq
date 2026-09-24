package com.opencrm.gateway.security;

import com.opencrm.gateway.config.SecurityConfig;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * ADR-016 decision 3 through real routing: a request travels the security chain, {@link IdentityRelayFilter} and Spring
 * Cloud Gateway's Netty proxy to an upstream on a loopback port, which reports the identity headers it received.
 */
@Tag("unit")
@DisplayName("ADR-016 D3: what an upstream service receives through the gateway")
class IdentityRelayRoutingTest {

    /** Lite configuration: not scanned by the whole-application tests. */
    static class TestKeyDecoder {
        @Bean
        ReactiveJwtDecoder testKeyDecoder(OpenCrmSecurityProperties properties) {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
            decoder.setJwtValidator(OpenCrmJwtDecoders.validator(properties.getJwt()));
            return decoder;
        }
    }

    private static HttpServer upstream() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var headers = exchange.getRequestHeaders();
            byte[] body = ("auth=" + headers.getFirst("Authorization") + "|user=" + headers.getFirst("X-User-Id")
                    + "|actor=" + headers.getFirst("X-Actor")).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        return server;
    }

    private static void throughGateway(String profile, Consumer<WebTestClient> test) throws Exception {
        HttpServer upstream = upstream();
        try {
            RouteDefinition route = new RouteDefinition();
            route.setId("echo");
            route.setUri(URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()));
            route.setPredicates(java.util.List.of(new PredicateDefinition("Path=/api/v1/**")));
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class, WebFluxAutoConfiguration.class,
                            GatewayAutoConfiguration.class))
                    .withUserConfiguration(TestKeyDecoder.class, SecurityConfig.class, GatewaySessionConfiguration.class)
                    .withBean("syntheticRoutes", RouteDefinitionLocator.class, () -> () -> Flux.just(route))
                    .withPropertyValues("spring.profiles.active=" + profile,
                            "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER)
                    .run(context -> {
                        var gateway = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                                .handle(new ReactorHttpHandlerAdapter(
                                        WebHttpHandlerBuilder.applicationContext(context).build()))
                                .bindNow();
                        try {
                            test.accept(WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gateway.port())
                                    .responseTimeout(Duration.ofSeconds(10)).build());
                        } finally {
                            gateway.disposeNow();
                        }
                    });
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    @DisplayName("bearer lane: the upstream gets the validated token and none of the spoofed identity headers")
    void bearerLane() throws Exception {
        String token = TestTokens.user("alice", "CPQ.User").mint();
        throughGateway("default", client -> client.get().uri("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-User-Id", "ceo")
                .header("X-Actor", "cfo")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("auth=Bearer " + token + "|user=null|actor=null"));
    }

    @Test
    @DisplayName("dev mode: an unvalidated Authorization header and identity headers never reach the upstream")
    void devModeStripsEverything() throws Exception {
        throughGateway("dev", client -> client.get().uri("/api/v1/quotes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-validated-in-dev")
                .header("X-User-Id", "ceo")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("auth=null|user=null|actor=null"));
    }
}
