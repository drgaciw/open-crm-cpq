package com.opencrm.gateway.routes;

import com.opencrm.gateway.controller.FallbackController;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.assertj.AssertableReactiveWebApplicationContext;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JAutoConfiguration;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayNoLoadBalancerClientAutoConfiguration;
import org.springframework.cloud.gateway.config.GatewayResilience4JCircuitBreakerAutoConfiguration;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-528 (BC-00-08): every route family other than quote and pricing binds its intended breaker, forwards to a
 * real fallback and has a finite, named, configurable deadline. Follows {@link GatewayCircuitBreakerTimeoutTest}
 * (PR 581): packaged route definitions, the actual reactive Resilience4j factory and real Netty forwarding.
 * Redis, auth and default filters are isolated here; their dedicated tests cover them.
 */
@Tag("unit")
class GatewayRouteFamilyCircuitBreakerTest {

    private static final String APPLICATION_YML = "src/main/resources/application.yml";
    private static final String ROUTES_KEY = "spring.cloud.gateway.server.webflux.routes";
    private static final List<HttpMethod> ALL_METHODS =
            List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    /** One breaker, fallback and deadline per downstream service. */
    record Family(String breaker, String property, Duration deadline, String fallback, String service) {
        String unavailableMessage() {
            return service + " is currently unavailable. Please try again later.";
        }
    }

    static final Family CRM = new Family("crmCircuitBreaker", "CRM_GATEWAY_TIMEOUT",
            Duration.ofSeconds(10), "crm", "CRM Service");
    static final Family PRICEBOOK = new Family("pricebookCircuitBreaker", "PRICEBOOK_GATEWAY_TIMEOUT",
            Duration.ofSeconds(10), "pricebook", "Price Book Service");
    static final Family CONFIGURATION = new Family("configurationCircuitBreaker", "CONFIGURATION_GATEWAY_TIMEOUT",
            Duration.ofSeconds(45), "configuration", "Configuration Engine");
    static final Family PRODUCT = new Family("productCircuitBreaker", "PRODUCT_GATEWAY_TIMEOUT",
            Duration.ofSeconds(10), "product", "Product Service");
    static final Family APPROVAL = new Family("approvalCircuitBreaker", "APPROVAL_GATEWAY_TIMEOUT",
            Duration.ofSeconds(10), "approval", "Approval Routing Service");
    static final Family DOCUMENT = new Family("documentCircuitBreaker", "DOCUMENT_GATEWAY_TIMEOUT",
            Duration.ofSeconds(10), "document", "Document Generation Service");
    static final Family CONTRACT = new Family("contractCircuitBreaker", "CONTRACT_GATEWAY_TIMEOUT",
            Duration.ofSeconds(10), "contract", "Contract/Asset Management Service");
    static final Family COMMUNICATION = new Family("communicationCircuitBreaker", "COMMUNICATION_GATEWAY_TIMEOUT",
            Duration.ofSeconds(10), "communication", "Communication Service");
    static final Family AI = new Family("aiCircuitBreaker", "AI_GATEWAY_TIMEOUT",
            Duration.ofSeconds(120), "ai", "AI Service");

    static final List<Family> FAMILIES =
            List.of(CRM, PRICEBOOK, CONFIGURATION, PRODUCT, APPROVAL, DOCUMENT, CONTRACT, COMMUNICATION, AI);

    record RouteCase(String routeId, Family family, String path, List<HttpMethod> methods) {
        @Override
        public String toString() {
            return routeId;
        }
    }

    /** Every packaged route still carrying the shortcut before DRG-528, aggregate owners and CPQ aliases. */
    static final List<RouteCase> ROUTES = List.of(
            new RouteCase("crm-service", CRM, "/api/v1/leads/1", ALL_METHODS),
            new RouteCase("pricebook-service", PRICEBOOK, "/api/v1/price-books/1", ALL_METHODS),
            new RouteCase("cpq-pricebook-service", PRICEBOOK, "/api/v1/cpq/price-books/1", ALL_METHODS),
            // The validate shim's predicates admit POST only (S2B-11).
            new RouteCase("configuration-validate", CONFIGURATION, "/api/v1/configuration/validate",
                    List.of(HttpMethod.POST)),
            new RouteCase("cpq-configuration-validate", CONFIGURATION, "/api/v1/cpq/configuration/validate",
                    List.of(HttpMethod.POST)),
            new RouteCase("product-service", PRODUCT, "/api/v1/products/1", ALL_METHODS),
            new RouteCase("cpq-product-service", PRODUCT, "/api/v1/cpq/products/1", ALL_METHODS),
            new RouteCase("configuration-engine", CONFIGURATION, "/api/v1/configurations/1", ALL_METHODS),
            new RouteCase("cpq-configuration-engine", CONFIGURATION, "/api/v1/cpq/configurations/1", ALL_METHODS),
            new RouteCase("approval-routing", APPROVAL, "/api/v1/approvals/1", ALL_METHODS),
            new RouteCase("cpq-approval-routing", APPROVAL, "/api/v1/cpq/approvals/1", ALL_METHODS),
            new RouteCase("document-generation", DOCUMENT, "/api/v1/documents/jobs/1", ALL_METHODS),
            new RouteCase("cpq-document-generation", DOCUMENT, "/api/v1/cpq/documents/jobs/1", ALL_METHODS),
            new RouteCase("contract-asset-management", CONTRACT, "/api/v1/subscriptions/1", ALL_METHODS),
            new RouteCase("cpq-contract-asset-management", CONTRACT, "/api/v1/cpq/subscriptions/1", ALL_METHODS),
            new RouteCase("communication-service", COMMUNICATION, "/api/v1/emails/1", ALL_METHODS),
            new RouteCase("ai-service", AI, "/api/v1/ai/agents/pricing/invoke", ALL_METHODS)
    );

    static Stream<RouteCase> routes() {
        return ROUTES.stream();
    }

    // ---------------------------------------------------------------------------------------------
    // Binding: the shortcut form bound `_genkey_0=name=...`, a literal breaker ID, and dropped the fallback.
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyPackagedBreakerUsesStructuredNameAndFallbackArguments() throws Exception {
        for (RouteDefinition route : packagedRoutes()) {
            if (route.getId().equals("erp-stub")) {
                continue; // forward-only placeholder with no upstream (S2A-7)
            }
            assertThat(route.getFilters()).as(route.getId())
                    .filteredOn(filter -> filter.getName().equals("CircuitBreaker"))
                    .singleElement().satisfies(filter -> {
                        assertThat(filter.getArgs()).as(route.getId()).containsOnlyKeys("name", "fallbackUri");
                        assertThat(filter.getArgs().get("name")).as(route.getId()).matches("[a-z]+CircuitBreaker");
                        assertThat(filter.getArgs().get("fallbackUri")).as(route.getId()).startsWith("forward:/fallback/");
                    });
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void routeBindsItsFamilyBreakerAndFallback(RouteCase c) throws Exception {
        FilterDefinition breaker = packagedRoute(c.routeId()).getFilters().stream()
                .filter(filter -> filter.getName().equals("CircuitBreaker")).findFirst().orElseThrow();
        assertThat(breaker.getArgs())
                .containsEntry("name", c.family().breaker())
                .containsEntry("fallbackUri", "forward:/fallback/" + c.family().fallback());
    }

    // ---------------------------------------------------------------------------------------------
    // Deadlines: finite, named, configurable, and not the library's one-second default.
    // ---------------------------------------------------------------------------------------------

    @Test
    void everyFamilyHasItsNamedFiniteDeadline() throws Exception {
        Map<Family, Duration> defaults = new LinkedHashMap<>();
        for (Family family : FAMILIES) {
            defaults.put(family, family.deadline());
        }
        gatewayRunner(null).run(context -> {
            assertThat(context).hasNotFailed();
            assertDeadlines(context, defaults);
        });
    }

    @Test
    void everyDeadlineIsConfigurableThroughItsNamedProperty() throws Exception {
        Map<Family, Duration> overrides = new LinkedHashMap<>();
        for (int i = 0; i < FAMILIES.size(); i++) {
            overrides.put(FAMILIES.get(i), Duration.ofSeconds(2L + i));
        }
        gatewayRunner(null)
                .withPropertyValues(overrides.entrySet().stream()
                        .map(e -> e.getKey().property() + "=" + e.getValue().toSeconds() + "s").toArray(String[]::new))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertDeadlines(context, overrides);
                });
    }

    // ---------------------------------------------------------------------------------------------
    // Real Netty forwarding through the actual reactive breaker.
    // ---------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void slowSuccessfulUpstreamWithinTheDeadlinePassesThroughOnTheIntendedBreaker(RouteCase c) throws Exception {
        // 1.6 s exceeds the library's 1 s default that a literal `name=...` breaker would have used.
        DisposableServer upstream = delayedUpstream(Duration.ofMillis(1600));
        try {
            throughGateway(gatewayRunner(towards(c, upstream)), (client, context) -> {
                client.post().uri(c.path()).exchange().expectStatus().isOk()
                        .expectBody(String.class).isEqualTo("served");
                CircuitBreaker breaker = context.getBean(CircuitBreakerRegistry.class).circuitBreaker(c.family().breaker());
                assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).as(c.family().breaker()).isEqualTo(1);
                assertNoLiteralShortcutIds(context);
            });
        } finally {
            upstream.disposeNow();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    void shortBudgetReturnsTheFamilyFallbackForEveryMethod(RouteCase c) throws Exception {
        DisposableServer upstream = delayedUpstream(Duration.ofSeconds(2));
        try {
            throughGateway(gatewayRunner(towards(c, upstream)).withPropertyValues(c.family().property() + "=150ms"),
                    (client, context) -> {
                        for (HttpMethod method : c.methods()) {
                            client.method(method).uri(c.path()).exchange()
                                    .expectStatus().isEqualTo(503)
                                    .expectBody()
                                    .jsonPath("$.success").isEqualTo(false)
                                    .jsonPath("$.message").isEqualTo(c.family().unavailableMessage());
                        }
                        assertNoLiteralShortcutIds(context);
                    });
        } finally {
            upstream.disposeNow();
        }
    }

    /** ADR-009: communication has no registry in-tree; its packaged `lb://` destination degrades to its fallback. */
    @Test
    void communicationKeepsItsUnregisteredDestinationAndDegradesToItsFallback() throws Exception {
        RouteDefinition route = packagedRoute("communication-service");
        assertThat(route.getUri()).isEqualTo(URI.create("lb://communication-service"));
        throughGateway(gatewayRunner(route), (client, context) -> {
            for (HttpMethod method : ALL_METHODS) {
                client.method(method).uri("/api/v1/emails/1").exchange()
                        .expectStatus().isEqualTo(503)
                        .expectBody().jsonPath("$.message").isEqualTo(COMMUNICATION.unavailableMessage());
            }
            assertNoLiteralShortcutIds(context);
        });
    }

    /**
     * The breaker completes when the upstream's response headers arrive, so an ai-service SSE stream that has
     * started is not cut by the deadline while its body continues (agent and orchestration {@code /stream}).
     */
    @Test
    void aiStreamThatStartedWithinTheDeadlineIsNotCutWhileItsBodyContinues() throws Exception {
        DisposableServer upstream = HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> response.header("Content-Type", "text/event-stream")
                        .sendString(Flux.concat(Mono.just("data: 0\n\n"),
                                Flux.interval(Duration.ofMillis(200)).take(3).map(i -> "data: " + (i + 1) + "\n\n"))))
                .bindNow();
        try {
            RouteCase ai = ROUTES.stream().filter(c -> c.family() == AI).findFirst().orElseThrow();
            throughGateway(gatewayRunner(towards(ai, upstream)).withPropertyValues(AI.property() + "=150ms"),
                    (client, context) -> client.post().uri("/api/v1/ai/agents/pricing/stream").exchange()
                            .expectStatus().isOk()
                            .expectBody(String.class).isEqualTo("data: 0\n\ndata: 1\n\ndata: 2\n\ndata: 3\n\n"));
        } finally {
            upstream.disposeNow();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fixture
    // ---------------------------------------------------------------------------------------------

    private static void assertDeadlines(AssertableReactiveWebApplicationContext context, Map<Family, Duration> expected) {
        TimeLimiterRegistry registry = context.getBean(TimeLimiterRegistry.class);
        expected.forEach((family, deadline) -> {
            // The instance and the named config (Cloud's reactive factory resolves configs by breaker ID).
            assertThat(registry.timeLimiter(family.breaker()).getTimeLimiterConfig().getTimeoutDuration())
                    .as(family.breaker() + " instance").isEqualTo(deadline);
            assertThat(registry.getConfiguration(family.breaker())).as(family.breaker() + " config")
                    .get().extracting(TimeLimiterConfig::getTimeoutDuration).isEqualTo(deadline);
        });
    }

    private static void assertNoLiteralShortcutIds(AssertableReactiveWebApplicationContext context) {
        assertThat(context.getBean(CircuitBreakerRegistry.class).getAllCircuitBreakers())
                .extracting(CircuitBreaker::getName).noneMatch(name -> name.startsWith("name="));
        assertThat(context.getBean(TimeLimiterRegistry.class).getAllTimeLimiters())
                .extracting(TimeLimiter::getName).noneMatch(name -> name.startsWith("name="));
    }

    private static DisposableServer delayedUpstream(Duration delay) {
        return HttpServer.create().host("127.0.0.1").port(0)
                .handle((request, response) -> Mono.delay(delay)
                        .then(response.header("Content-Type", "text/plain")
                                .sendString(Mono.just("served")).then()))
                .bindNow();
    }

    /** The packaged route, filters unchanged, pointed at a local upstream. */
    private static RouteDefinition towards(RouteCase c, DisposableServer upstream) throws Exception {
        RouteDefinition route = packagedRoute(c.routeId());
        route.setUri(URI.create("http://127.0.0.1:" + upstream.port()));
        return route;
    }

    private static List<RouteDefinition> packagedRoutes() throws Exception {
        var source = new YamlPropertySourceLoader().load("gateway", new FileSystemResource(APPLICATION_YML)).getFirst();
        var environment = new StandardEnvironment();
        // Keep developer shell variables from affecting the packaged defaults.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(source);
        return Binder.get(environment).bind(ROUTES_KEY, Bindable.listOf(RouteDefinition.class))
                .orElseThrow(() -> new AssertionError("Missing gateway routes"));
    }

    private static RouteDefinition packagedRoute(String routeId) throws Exception {
        return packagedRoutes().stream().filter(route -> route.getId().equals(routeId)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing route " + routeId));
    }

    /** The packaged resilience4j settings (placeholders unresolved, so named properties still apply). */
    private static ReactiveWebApplicationContextRunner gatewayRunner(RouteDefinition route) throws Exception {
        var source = new YamlPropertySourceLoader().load("gateway", new FileSystemResource(APPLICATION_YML)).getFirst();
        Map<String, Object> resilience = new LinkedHashMap<>();
        for (String name : ((EnumerablePropertySource<?>) source).getPropertyNames()) {
            if (name.startsWith("resilience4j.")) {
                resilience.put(name, source.getProperty(name));
            }
        }
        var runner = new ReactiveWebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class, WebFluxAutoConfiguration.class,
                        BulkheadAutoConfiguration.class, CircuitBreakerAutoConfiguration.class,
                        TimeLimiterAutoConfiguration.class, Resilience4JAutoConfiguration.class,
                        ReactiveResilience4JAutoConfiguration.class, GatewayAutoConfiguration.class,
                        GatewayResilience4JCircuitBreakerAutoConfiguration.class,
                        // The gateway has no load balancer: `lb://` URIs fail inside the breaker, as packaged.
                        GatewayNoLoadBalancerClientAutoConfiguration.class))
                .withUserConfiguration(FallbackController.class)
                .withInitializer(context -> context.getEnvironment().getPropertySources()
                        .addLast(new MapPropertySource("packaged-resilience", resilience)));
        return route == null ? runner
                : runner.withBean("syntheticRoutes", RouteDefinitionLocator.class, () -> () -> Flux.just(route));
    }

    private static void throughGateway(ReactiveWebApplicationContextRunner runner,
                                       BiConsumer<WebTestClient, AssertableReactiveWebApplicationContext> assertion) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            DisposableServer gateway = HttpServer.create().host("127.0.0.1").port(0)
                    .handle(new ReactorHttpHandlerAdapter(WebHttpHandlerBuilder.applicationContext(context).build()))
                    .bindNow();
            try {
                assertion.accept(WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + gateway.port())
                        .responseTimeout(Duration.ofSeconds(10)).build(), context);
            } finally {
                gateway.disposeNow();
            }
        });
    }
}
