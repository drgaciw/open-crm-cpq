package com.opencrm.gateway.routes;

import com.opencrm.gateway.GatewayApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.web.reactive.server.WebTestClient;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Whole application configuration regression: Boot4, JWT and actual reactive breaker factories. */
@Tag("unit")
class GatewayApplicationStartupTest {
    @ParameterizedTest
    @ValueSource(strings = {"default", "dev"})
    void packagedConfigurationBootstrapsWithoutAzureCredentialBootstrap(String profile) {
        new ReactiveWebApplicationContextRunner().withUserConfiguration(GatewayApplication.class)
                .withInitializer(context -> context.getEnvironment().setConversionService(new ApplicationConversionService()))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile,
                        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SpringCloudCircuitBreakerResilience4JFilterFactory.class);
                    var routes = context.getBean(RouteLocator.class).getRoutes().collectList().block(Duration.ofSeconds(10));
                    assertThat(routes).anySatisfy(route -> {
                        assertThat(route.getId()).isEqualTo("ai-service");
                        assertThat(route.getUri().toString()).isEqualTo("http://localhost:8090");
                    });
                    if (profile.equals("default")) {
                        WebTestClient.bindToApplicationContext(context).build().get().uri("/api/v1/ai/agents")
                                .exchange().expectStatus().isUnauthorized();
                    }
                });
    }
}
