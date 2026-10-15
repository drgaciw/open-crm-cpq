package com.opencrm.gateway.routes;

import com.opencrm.gateway.controller.FallbackController;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.assertj.core.api.Assertions.assertThat;

/** Retains the packaged route's real circuit breaker around a slow, successful upstream. */
@Tag("unit")
class GatewayCircuitBreakerTimeoutTest {

    @ParameterizedTest
    @ValueSource(strings = {"quote-service", "cpq-quote-service", "pricing-service", "cpq-pricing-service"})
    void calculationThatExceedsOneSecondStillReturnsItsSuccessfulResponse(String routeId) throws Exception {
        throughGateway(routeId, Duration.ofMillis(1600), null, client ->
                client.post().uri(path(routeId)).exchange().expectStatus().isOk()
                        .expectBody(String.class).isEqualTo("calculated"));
    }

    @Test
    void quoteAndPricingHaveFiniteBudgetsAlignedWithTheQuoteClient() throws Exception {
        configuredRunner().run(context -> {
            assertThat(context).hasNotFailed();
            TimeLimiterRegistry registry = context.getBean(TimeLimiterRegistry.class);
            assertThat(registry.timeLimiter("quoteCircuitBreaker").getTimeLimiterConfig().getTimeoutDuration())
                    .isEqualTo(Duration.ofSeconds(45));
            assertThat(registry.timeLimiter("pricingCircuitBreaker").getTimeLimiterConfig().getTimeoutDuration())
                    .isEqualTo(Duration.ofSeconds(35));
            // The other route families' budgets: GatewayRouteFamilyCircuitBreakerTest (DRG-528).
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"quote-service", "cpq-quote-service", "pricing-service", "cpq-pricing-service"})
    void configuredShortBudgetStillReturnsTheUnavailableFallbackForAnOverBudgetPost(String routeId) throws Exception {
        throughGateway(routeId, Duration.ofSeconds(2), Duration.ofMillis(150), client ->
                client.post().uri(path(routeId)).exchange().expectStatus().isEqualTo(503)
                        .expectBody().jsonPath("$.success").isEqualTo(false)
                        .jsonPath("$.message").value(message -> assertThat(message.toString()).contains("currently unavailable")));
    }

    private static String path(String routeId) {
        String prefix = routeId.startsWith("cpq-") ? "/api/v1/cpq/" : "/api/v1/";
        return prefix + (routeId.contains("quote") ? "quotes/1/calculate" : "pricing/execute");
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
                        BulkheadAutoConfiguration.class, CircuitBreakerAutoConfiguration.class, TimeLimiterAutoConfiguration.class,
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
            RouteDefinition route = configured.stream().filter(r -> r.getId().equals(routeId)).findFirst().orElseThrow();
            route.setUri(URI.create("http://127.0.0.1:" + upstream.port()));
            assertThat(route.getFilters()).anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("CircuitBreaker"));
            var runner = configuredRunner()
                    .withBean("syntheticRoutes", RouteDefinitionLocator.class, () -> () -> Flux.just(route));
            if (shortBudget != null) {
                String breaker = routeId.contains("quote") ? "quoteCircuitBreaker" : "pricingCircuitBreaker";
                runner = runner.withPropertyValues(
                        "resilience4j.timelimiter.configs." + breaker + ".timeout-duration=" + shortBudget.toMillis() + "ms");
            }
            runner.run(context -> {
                assertThat(context).hasNotFailed();
                var gateway = reactor.netty.http.server.HttpServer.create().host("127.0.0.1").port(0)
                        .handle(new ReactorHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(context).build())).bindNow();
                try {
                    assertion.accept(WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gateway.port())
                            .responseTimeout(Duration.ofSeconds(10)).build());
                    // Shortcut `CircuitBreaker=name=...` used to create a literal "name=..." ID,
                    // silently missing these named limits and ignoring the fallback argument.
                    assertThat(context.getBean(TimeLimiterRegistry.class).getAllTimeLimiters())
                            .extracting(io.github.resilience4j.timelimiter.TimeLimiter::getName)
                            .noneMatch(name -> name.startsWith("name="));
                } finally {
                    gateway.disposeNow();
                }
            });
        } finally {
            upstream.disposeNow();
        }
    }
}
