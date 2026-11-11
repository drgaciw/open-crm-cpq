package com.opencrm.gateway.routes;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Both shipped profiles must dispatch the real activity API to its CRM owner. */
@Tag("unit")
class ActivityGatewayRouteTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void activitiesUseCrmPredicateAndPreserveOtherRouteBoundaries(boolean dev) throws Exception {
        var sources = new YamlPropertySourceLoader().load("gateway",
                new FileSystemResource("src/main/resources/application.yml"));
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(sources.get(dev ? 1 : 0));
        var routes = Binder.get(environment).bind("spring.cloud.gateway.server.webflux.routes",
                Bindable.listOf(RouteDefinition.class)).get();
        var crm = routes.stream().filter(route -> route.getId().equals("crm-service")).findFirst().orElseThrow();
        assertThat(crm.getUri().toString()).isEqualTo("http://localhost:8081");
        var path = crm.getPredicates().stream().filter(predicate -> predicate.getName().equals("Path"))
                .findFirst().orElseThrow();
        var predicate = new PathRoutePredicateFactory(new WebFluxProperties()).apply(new PathRoutePredicateFactory.Config()
                .setPatterns(new ArrayList<>(path.getArgs().values())));

        for (String endpoint : new String[]{"/api/v1/activities", "/api/v1/activities/42",
                "/api/v1/leads/42", "/api/v1/deals/42", "/api/v1/contacts/42", "/api/v1/organizations/42"}) {
            assertThat(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get(endpoint))))
                    .as("CRM predicate accepts %s (dev=%s)", endpoint, dev).isTrue();
        }
        for (String endpoint : new String[]{"/api/v1/mailboxes/42", "/api/v1/quotes/42", "/api/v1/activities-other/42"}) {
            assertThat(predicate.test(MockServerWebExchange.from(MockServerHttpRequest.get(endpoint))))
                    .as("CRM predicate keeps %s out (dev=%s)", endpoint, dev).isFalse();
        }
    }
}
