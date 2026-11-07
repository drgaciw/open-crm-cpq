package com.opencrm.gateway.routes;

import com.opencrm.gateway.controller.FallbackController;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayResilience4JCircuitBreakerAutoConfiguration;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Retains each packaged route family's real circuit breaker around a slow, successful upstream. */
@Tag("unit")
class GatewayCircuitBreakerTimeoutTest {

    private static final Map<String, String> ROUTE_BREAKERS = Map.ofEntries(
            Map.entry("crm-service", "crmCircuitBreaker"),
            Map.entry("pricing-service", "pricingCircuitBreaker"),
            Map.entry("cpq-pricing-service", "pricingCircuitBreaker"),
            Map.entry("pricebook-service", "pricebookCircuitBreaker"),
            Map.entry("cpq-pricebook-service", "pricebookCircuitBreaker"),
            Map.entry("configuration-validate", "configurationCircuitBreaker"),
            Map.entry("cpq-configuration-validate", "configurationCircuitBreaker"),
            Map.entry("product-service", "productCircuitBreaker"),
            Map.entry("cpq-product-service", "productCircuitBreaker"),
            Map.entry("quote-service", "quoteCircuitBreaker"),
            Map.entry("cpq-quote-service", "quoteCircuitBreaker"),
            Map.entry("configuration-engine", "configurationCircuitBreaker"),
            Map.entry("cpq-configuration-engine", "configurationCircuitBreaker"),
            Map.entry("approval-routing", "approvalCircuitBreaker"),
            Map.entry("cpq-approval-routing", "approvalCircuitBreaker"),
            Map.entry("document-generation", "documentCircuitBreaker"),
            Map.entry("cpq-document-generation", "documentCircuitBreaker"),
            Map.entry("contract-asset-management", "contractCircuitBreaker"),
            Map.entry("cpq-contract-asset-management", "contractCircuitBreaker"),
            Map.entry("communication-service", "communicationCircuitBreaker"),
            Map.entry("ai-service", "aiCircuitBreaker")
    );

    private static final Map<String, Duration> BREAKER_BUDGETS = Map.ofEntries(
            Map.entry("quoteCircuitBreaker", Duration.ofSeconds(45)),
            Map.entry("pricingCircuitBreaker", Duration.ofSeconds(35)),
            Map.entry("crmCircuitBreaker", Duration.ofSeconds(30)),
            Map.entry("pricebookCircuitBreaker", Duration.ofSeconds(35)),
            Map.entry("productCircuitBreaker", Duration.ofSeconds(30)),
            Map.entry("configurationCircuitBreaker", Duration.ofSeconds(35)),
            Map.entry("approvalCircuitBreaker", Duration.ofSeconds(30)),
            Map.entry("documentCircuitBreaker", Duration.ofSeconds(45)),
            Map.entry("contractCircuitBreaker", Duration.ofSeconds(30)),
            Map.entry("communicationCircuitBreaker", Duration.ofSeconds(30)),
            Map.entry("aiCircuitBreaker", Duration.ofSeconds(45))
    );

    static Stream<Arguments> routeFamilies() {
        return ROUTE_BREAKERS.keySet().stream().sorted().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("routeFamilies")
    void slowSuccessfulUpstreamWithinDeadlinePassesThrough(String routeId) throws Exception {
        throughGateway(routeId, Duration.ofMillis(1600), null, client ->
                client.post().uri(path(routeId)).exchange().expectStatus().isOk()
                        .expectBody(String.class).isEqualTo("calculated"));
    }

    @ParameterizedTest
    @MethodSource("routeFamilies")
    void configuredShortBudgetStillReturnsTheUnavailableFallbackForAnOverBudgetPost(String routeId)
            throws Exception {
        throughGateway(routeId, Duration.ofSeconds(2), Duration.ofMillis(150), client ->
                client.post().uri(path(routeId)).exchange().expectStatus().isEqualTo(503)
                        .expectBody().jsonPath("$.success").isEqualTo(false)
                        .jsonPath("$.message").value(message ->
                                assertThat(message.toString()).contains("currently unavailable")));
    }

    @Test
    void everyBreakerHasANamedFiniteBudget() throws Exception {
        configuredRunner().run(context -> {
            assertThat(context).hasNotFailed();
            TimeLimiterRegistry registry = context.getBean(TimeLimiterRegistry.class);
            BREAKER_BUDGETS.forEach((breaker, expected) ->
                    assertThat(registry.timeLimiter(breaker).getTimeLimiterConfig().getTimeoutDuration())
                            .as(breaker)
                            .isEqualTo(expected));
        });
    }

    private static String path(String routeId) {
        boolean cpq = routeId.startsWith("cpq-");
        String prefix = cpq ? "/api/v1/cpq/" : "/api/v1/";
        return switch (routeId) {
            case "crm-service" -> prefix + "leads/1";
            case "pricing-service", "cpq-pricing-service" -> prefix + "pricing/execute";
            case "pricebook-service", "cpq-pricebook-service" -> prefix + "price-books/1";
            case "configuration-validate" -> "/api/v1/configuration/validate";
            case "cpq-configuration-validate" -> "/api/v1/cpq/configuration/validate";
            case "product-service", "cpq-product-service" -> prefix + "products/1";
            case "quote-service", "cpq-quote-service" -> prefix + "quotes/1/calculate";
            case "configuration-engine", "cpq-configuration-engine" -> prefix + "configurations/1";
            case "approval-routing", "cpq-approval-routing" -> prefix + "approvals/1";
            case "document-generation", "cpq-document-generation" -> prefix + "documents/1";
            case "contract-asset-management", "cpq-contract-asset-management" -> prefix + "subscriptions/1";
            case "communication-service" -> prefix + "emails/1";
            case "ai-service" -> prefix + "ai/agents";
            default -> throw new IllegalArgumentException("Unknown route family: " + routeId);
        };
    }

    private static String breakerFor(String routeId) {
        String breaker = ROUTE_BREAKERS.get(routeId);
        if (breaker == null) {
            throw new IllegalArgumentException("Unknown route breaker: " + routeId);
        }
        return breaker;
    }

    private static ReactiveWebApplicationContextRunner configuredRunner() throws Exception {
        var source = new YamlPropertySourceLoader().load("gateway",
                new FileSystemResource("src/main/resources/application.yml")).getFirst();
        var resilience = new java.util.LinkedHashMap<String, Object>();
        for (String name : ((org.springframework.core.env.EnumerablePropertySource<?>) source).getPropertyNames()) {
            if (name.startsWith("resilience4j.")) {
                resilience.put(name, source.getProperty(name));
            }
        }
        return new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class, WebFluxAutoConfiguration.class,
                        BulkheadAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
                        TimeLimiterAutoConfiguration.class,
                        Resilience4JAutoConfiguration.class, ReactiveResilience4JAutoConfiguration.class,
                        GatewayAutoConfiguration.class, GatewayResilience4JCircuitBreakerAutoConfiguration.class))
                .withUserConfiguration(FallbackController.class)
                .withInitializer(context -> {
                    // Use the packaged resilience settings; routing below uses its original filter definitions.
                    // Redis/auth/default filters are covered by the existing security and whole-app tests.
                    context.getEnvironment().getPropertySources().addLast(
                            new org.springframework.core.env.MapPropertySource("packaged-resilience", resilience));
                });
    }

    private static void throughGateway(String routeId, Duration upstreamDelay, Duration shortBudget,
                                       java.util.function.Consumer<WebTestClient> assertion) throws Exception {
        var upstream = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> Mono.delay(upstreamDelay)
                        .then(response.header("Content-Type", "text/plain")
                                .sendString(Mono.just("calculated")).then())).bindNow();
        try {
            var source = new YamlPropertySourceLoader().load("gateway",
                    new FileSystemResource("src/main/resources/application.yml")).getFirst();
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().addFirst(source);
            var configured = Binder.get(environment).bind("spring.cloud.gateway.server.webflux.routes",
                    Bindable.listOf(RouteDefinition.class)).get();
            RouteDefinition route = configured.stream().filter(r -> r.getId().equals(routeId)).findFirst()
                    .orElseThrow();
            route.setUri(URI.create("http://127.0.0.1:" + upstream.port()));
            assertThat(route.getFilters()).anySatisfy(filter -> {
                assertThat(filter.getName()).isEqualTo("CircuitBreaker");
                assertThat(filter.getArgs()).containsEntry("name", breakerFor(routeId));
                assertThat(filter.getArgs().get("name").toString()).doesNotStartWith("name=");
            });
            var runner = configuredRunner()
                    .withBean("syntheticRoutes", RouteDefinitionLocator.class, () -> () -> Flux.just(route));
            if (shortBudget != null) {
                String breaker = breakerFor(routeId);
                runner = runner.withPropertyValues(
                        "resilience4j.timelimiter.configs." + breaker + ".timeout-duration="
                                + shortBudget.toMillis() + "ms");
            }
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                var gateway = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                        .handle(new ReactorHttpHandlerAdapter(
                                WebHttpHandlerBuilder.applicationContext(context).build())).bindNow();
                try {
                    assertion.accept(WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gateway.port())
                            .responseTimeout(Duration.ofSeconds(10)).build());
                    // Shortcut `CircuitBreaker=name=...` used to create a literal "name=..." ID,
                    // silently missing these named limits and ignoring the fallback argument.
                    assertThat(context.getBean(TimeLimiterRegistry.class).getAllTimeLimiters())
                            .extracting(io.github.resilience4j.timelimiter.TimeLimiter::getName)
                            .noneMatch(name -> name.startsWith("name="))
                            .contains(breakerFor(routeId));
                } finally {
                    gateway.disposeNow();
                }
            });
        } finally {
            upstream.disposeNow();
        }
    }
}
