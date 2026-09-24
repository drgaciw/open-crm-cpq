package com.opencrm.gateway.routes;

import com.opencrm.gateway.GatewayApplication;
import com.opencrm.gateway.security.GatewayAuthenticationEntryPoint;
import com.opencrm.gateway.security.GatewayLogin;
import com.opencrm.gateway.security.IdentityRelayFilter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.cloud.gateway.filter.factory.SpringCloudCircuitBreakerResilience4JFilterFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.session.ReactiveMapSessionRepository;
import org.springframework.session.ReactiveSessionRepository;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Whole application configuration regression: Boot4, JWT and actual reactive breaker factories. */
@Tag("unit")
class GatewayApplicationStartupTest {

    private static ReactiveWebApplicationContextRunner gateway(String profile) {
        return new ReactiveWebApplicationContextRunner().withUserConfiguration(GatewayApplication.class)
                .withInitializer(context -> context.getEnvironment().setConversionService(new ApplicationConversionService()))
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile,
                        "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "dev"})
    void packagedConfigurationBootstraps(String profile) {
        gateway(profile)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SpringCloudCircuitBreakerResilience4JFilterFactory.class)
                            .hasSingleBean(IdentityRelayFilter.class);
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

    /** ADR-016 step 3: with a login configured, the packaged gateway is a BFF with Redis-backed sessions. */
    @Test
    void bffLoginBootstrapsWithRedisSessions() {
        gateway("default")
                .withPropertyValues(
                        "opencrm.security.jwt.issuer-uri=https://login.microsoftonline.com/00000000-0000-4000-8000-00000000c0de/v2.0",
                        "opencrm.gateway.login.client-id=gateway-client",
                        "opencrm.gateway.login.client-secret=local-test-secret")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(GatewayLogin.class);
                    assertThat(context.getBeansOfType(ReactiveSessionRepository.class).values())
                            .singleElement().satisfies(repository ->
                                    assertThat(repository.getClass().getName()).contains("Redis"));
                    var sessions = (DefaultWebSessionManager) context.getBean("webSessionManager");
                    assertThat(sessions.getSessionIdResolver()).isInstanceOfSatisfying(CookieWebSessionIdResolver.class,
                            resolver -> assertThat(resolver.getCookieName()).isEqualTo("OPENCRM_SESSION"));
                    WebTestClient.bindToApplicationContext(context).build().get().uri("/api/v1/ai/agents")
                            .exchange().expectStatus().isUnauthorized()
                            .expectHeader().valueEquals(GatewayAuthenticationEntryPoint.LOGIN_HINT_HEADER,
                                    "/oauth2/authorization/entra");
                });
    }

    /**
     * ADR-016 step 5: behind the TLS-terminating ingress, {@code SERVER_FORWARD_HEADERS_STRATEGY=framework} makes the
     * login's redirect URI the public https origin. Without it, forwarded headers are not trusted.
     */
    @Test
    void behindTheIngressTheRedirectUriIsThePublicOrigin() {
        // The login stores its authorization request in the session; an in-memory store replaces Redis here.
        var login = gateway("default").withPropertyValues(
                        "opencrm.security.jwt.issuer-uri=https://login.microsoftonline.com/00000000-0000-4000-8000-00000000c0de/v2.0",
                        "opencrm.gateway.login.client-id=gateway-client",
                        "opencrm.gateway.login.client-secret=local-test-secret")
                .withBean(ReactiveSessionRepository.class,
                        () -> new ReactiveMapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>()));
        login.withPropertyValues("server.forward-headers-strategy=framework").run(context -> {
            assertThat(context).hasNotFailed().hasBean("forwardedHeaderTransformer");
            assertThat(redirectUri(context)).isEqualTo("https://crm.example.com/login/oauth2/code/entra");
        });
        login.run(context -> assertThat(redirectUri(context)).doesNotContain("crm.example.com"));
    }

    private static String redirectUri(org.springframework.context.ApplicationContext context) {
        URI location = WebTestClient.bindToApplicationContext(context).build().get().uri("/oauth2/authorization/entra")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-Host", "crm.example.com")
                .header("X-Forwarded-Port", "443")
                .exchange().expectStatus().isFound()
                .returnResult(Void.class).getResponseHeaders().getLocation();
        assertThat(location).isNotNull();
        String encoded = UriComponentsBuilder.fromUri(location).build().getQueryParams().getFirst("redirect_uri");
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
    }
}
