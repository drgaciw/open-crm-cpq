package com.opencrm.gateway.routes;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bind real route definitions to catch profile list replacement and URI regressions. */
@Tag("unit")
@Tag("contract")
class AiGatewayRouteConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aiAndDocumentsHaveDistinctReachableLocalRoutes(boolean dev) throws Exception {
        var routes = routes(dev, Map.of());
        var ai = route(routes, "ai-service");
        assertEquals(URI.create("http://localhost:8090"), ai.getUri());
        assertEquals(URI.create("http://localhost:8086"), route(routes, "document-generation").getUri());
        assertTrue(ai.getPredicates().stream().anyMatch(p -> p.getName().equals("Path")
                && p.getArgs().containsValue("/api/v1/ai/**")
                && p.getArgs().containsValue("/api/v1/copilot/**")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitAiDestinationOverridesLocalDefault(boolean dev) throws Exception {
        assertEquals(URI.create("http://ai-service:8090"),
                route(routes(dev, Map.of("AI_SERVICE_URI", "http://ai-service:8090")), "ai-service").getUri());
    }

    private List<RouteDefinition> routes(boolean dev, Map<String, Object> overrides) throws Exception {
        var sources = new YamlPropertySourceLoader().load("gateway",
                new FileSystemResource("src/main/resources/application.yml"));
        var environment = new StandardEnvironment();
        // Keep developer shell variables from affecting this deterministic configuration test.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(sources.get(dev ? 1 : 0));
        environment.getPropertySources().addFirst(new MapPropertySource("test", overrides));
        return Binder.get(environment).bind("spring.cloud.gateway.server.webflux.routes",
                Bindable.listOf(RouteDefinition.class)).orElseThrow(() -> new AssertionError("Missing gateway routes"));
    }

    private RouteDefinition route(List<RouteDefinition> routes, String id) {
        assertEquals(1, routes.stream().filter(r -> id.equals(r.getId())).count());
        return routes.stream().filter(r -> id.equals(r.getId())).findFirst().orElseThrow();
    }
}
