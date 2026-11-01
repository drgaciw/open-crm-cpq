package com.opencrm.gateway.routes;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

/** Real Gateway predicate/rewrite/Netty forwarding with synthetic upstreams on ephemeral loopback ports. */
@Tag("unit")
class GatewayDispatchSmokeTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void configuredRoutesDispatchToDistinctUpstreamsAndKeepApprovalHistoryAtQuoteBff(boolean dev) throws Exception {
        HttpServer ai = upstream("ai"), documents = upstream("documents"), quote = upstream("quote"), contract = upstream("contract");
        try {
            var routes = configuredRoutes(dev, Map.of("ai-service", ai, "document-generation", documents,
                    "cpq-document-generation", documents, "quote-service", quote, "cpq-quote-service", quote,
                    "contract-asset-management", contract));
            new ReactiveWebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class, WebFluxAutoConfiguration.class, GatewayAutoConfiguration.class))
                    .withBean("syntheticRoutes", RouteDefinitionLocator.class, () -> () -> Flux.fromIterable(routes))
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        var gateway = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                                .handle(new ReactorHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(context).build())).bindNow();
                        try {
                            WebTestClient client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gateway.port())
                                    .responseTimeout(Duration.ofSeconds(10)).build();
                            get(client, "/api/v1/ai/agents", "ai:/api/v1/ai/agents");
                            get(client, "/api/v1/copilot/chat", "ai:/api/v1/copilot/chat");
                            get(client, "/api/v1/documents/jobs", "documents:/api/v1/documents/jobs");
                            get(client, "/api/v1/cpq/documents/jobs", "documents:/api/v1/documents/jobs");
                            get(client, "/api/v1/quotes/synthetic-quote/approvals/history", "quote:/api/v1/quotes/synthetic-quote/approvals/history");
                            get(client, "/api/v1/cpq/quotes/synthetic-quote/approvals/history", "quote:/api/v1/quotes/synthetic-quote/approvals/history");
                            get(client, "/api/v1/assets", "contract:/api/v1/assets");
                            get(client, "/api/v1/assets/42", "contract:/api/v1/assets/42");
                            get(client, "/api/v1/subscriptions/42", "contract:/api/v1/subscriptions/42");
                            client.get().uri("/api/v1/unmapped").exchange().expectStatus().isNotFound();
                        } finally { gateway.disposeNow(); }
                    });
        } finally { ai.stop(0); documents.stop(0); quote.stop(0); contract.stop(0); }
    }

    private List<RouteDefinition> configuredRoutes(boolean dev, Map<String, HttpServer> upstreams) throws Exception {
        var sources = new YamlPropertySourceLoader().load("gateway", new FileSystemResource("src/main/resources/application.yml"));
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(sources.get(dev ? 1 : 0));
        var all = Binder.get(environment).bind("spring.cloud.gateway.server.webflux.routes", Bindable.listOf(RouteDefinition.class)).get();
        var routes = all.stream().filter(r -> upstreams.containsKey(r.getId())).toList();
        assertThat(routes).hasSize(6);
        for (RouteDefinition route : routes) {
            route.setUri(URI.create("http://127.0.0.1:" + upstreams.get(route.getId()).getAddress().getPort()));
            // The fixture isolates routing: Redis/auth/circuit breaker behavior has dedicated tests.
            route.setFilters(route.getFilters().stream().filter(f -> !f.getName().equals("CircuitBreaker")).toList());
        }
        return routes;
    }

    private HttpServer upstream(String name) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = (name + ":" + exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        return server;
    }

    private void get(WebTestClient client, String path, String body) {
        client.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class).isEqualTo(body);
    }
}
