package com.opencrm.gateway.security;

import com.opencrm.gateway.config.SecurityConfig;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/** The gateway's security chain in {@code jwt} mode with and without the BFF login, and in {@code dev} mode. */
@Tag("unit")
@DisplayName("ADR-016 step 3: gateway security chain (BFF login, bearer lane, CSRF, entry point)")
class GatewaySecurityChainTest {

    private static final String TENANT_BASE = TestTokens.ISSUER.substring(0, TestTokens.ISSUER.length() - "/v2.0".length());

    /**
     * Deliberately neither {@code @Configuration} nor {@code @RestController}: the whole-application tests scan
     * {@code com.opencrm.gateway}, and test types must not leak into that scan.
     */
    @EnableWebFlux
    static class Echo {
        /** The bearer lane's decoder with the test key and the production validation rules. */
        @Bean
        ReactiveJwtDecoder testKeyDecoder(OpenCrmSecurityProperties properties) {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
            decoder.setJwtValidator(OpenCrmJwtDecoders.validator(properties.getJwt()));
            return decoder;
        }

        @Bean
        RouterFunction<ServerResponse> echoRoutes() {
            return RouterFunctions.route()
                    .GET("/api/v1/echo", request -> whoami())
                    .GET("/quotes", request -> whoami())
                    .GET("/actuator/health", request -> whoami())
                    .POST("/api/v1/echo", request -> whoami())
                    .build();
        }

        private static Mono<ServerResponse> whoami() {
            return ReactiveSecurityContextHolder.getContext()
                    .map(context -> context.getAuthentication().getName())
                    .defaultIfEmpty("anonymous")
                    .flatMap(name -> ServerResponse.ok().bodyValue(name));
        }
    }

    private static ReactiveWebApplicationContextRunner runner() {
        return new ReactiveWebApplicationContextRunner()
                .withUserConfiguration(Echo.class, SecurityConfig.class, GatewayLoginConfiguration.class,
                        GatewaySessionConfiguration.class)
                .withPropertyValues("opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER);
    }

    private static ReactiveWebApplicationContextRunner withLogin() {
        return runner().withPropertyValues("opencrm.gateway.login.client-id=gateway-client",
                "opencrm.gateway.login.client-secret=local-test-secret");
    }

    private static void run(ReactiveWebApplicationContextRunner runner, Consumer<WebTestClient> test) {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            test.accept(WebTestClient.bindToApplicationContext(context).build());
        });
    }

    @Nested
    @DisplayName("jwt mode with the BFF login")
    class WithLogin {

        @Test
        @DisplayName("an anonymous API call gets 401, a Bearer challenge and the login hint, not a redirect")
        void apiCallGets401WithLoginHint() {
            run(withLogin(), client -> client.get().uri("/api/v1/echo").accept(MediaType.APPLICATION_JSON)
                    .exchange()
                    .expectStatus().isUnauthorized()
                    .expectHeader().valueEquals(GatewayAuthenticationEntryPoint.LOGIN_HINT_HEADER,
                            "/oauth2/authorization/entra")
                    .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                    .expectBody().jsonPath("$.success").isEqualTo(false));
        }

        @Test
        @DisplayName("an anonymous page navigation is redirected to the login")
        void pageNavigationRedirectsToLogin() {
            run(withLogin(), client -> client.get().uri("/quotes").accept(MediaType.TEXT_HTML)
                    .exchange()
                    .expectStatus().isFound()
                    .expectHeader().location("/oauth2/authorization/entra"));
        }

        @Test
        @DisplayName("the login is an Entra authorization-code request with PKCE, nonce and the API scope")
        void loginStartsAuthorizationCodeWithPkce() {
            run(withLogin(), client -> {
                URI location = client.get().uri("/oauth2/authorization/entra").exchange()
                        .expectStatus().isFound()
                        .returnResult(Void.class).getResponseHeaders().getLocation();
                assertThat(location).isNotNull();
                UriComponents authorize = UriComponentsBuilder.fromUri(location).build();
                assertThat(location.toString()).startsWith(TENANT_BASE + "/oauth2/v2.0/authorize?");
                var query = authorize.getQueryParams();
                assertThat(query.getFirst("response_type")).isEqualTo("code");
                assertThat(query.getFirst("client_id")).isEqualTo("gateway-client");
                assertThat(query.getFirst("code_challenge_method")).isEqualTo("S256");
                assertThat(query.getFirst("code_challenge")).isNotBlank();
                assertThat(query.getFirst("nonce")).isNotBlank();
                assertThat(query.getFirst("state")).isNotBlank();
                assertThat(URLDecoder.decode(query.getFirst("scope"), StandardCharsets.UTF_8))
                        .isEqualTo("openid profile offline_access api://opencrm-api/.default");
                assertThat(URLDecoder.decode(query.getFirst("redirect_uri"), StandardCharsets.UTF_8))
                        .endsWith("/login/oauth2/code/entra");
            });
        }

        @Test
        @DisplayName("bearer lane: a valid token is accepted, and a POST needs no CSRF token")
        void bearerLane() {
            String bearer = TestTokens.user("alice", "CPQ.User").bearer();
            run(withLogin(), client -> {
                client.get().uri("/api/v1/echo").header(HttpHeaders.AUTHORIZATION, bearer).exchange()
                        .expectStatus().isOk().expectBody(String.class).isEqualTo("alice");
                client.post().uri("/api/v1/echo").header(HttpHeaders.AUTHORIZATION, bearer).exchange()
                        .expectStatus().isOk().expectBody(String.class).isEqualTo("alice");
            });
        }

        @Test
        @DisplayName("bearer lane: forged, expired and wrong-audience tokens get 401 invalid_token")
        void invalidBearer() {
            for (String bearer : new String[] {
                    TestTokens.user("alice").signedByForeignKey().bearer(),
                    TestTokens.user("alice").expiresAt(java.time.Instant.now().minusSeconds(600)).bearer(),
                    TestTokens.user("alice").audience("api://other").bearer()}) {
                run(withLogin(), client -> client.get().uri("/api/v1/echo").header(HttpHeaders.AUTHORIZATION, bearer)
                        .exchange()
                        .expectStatus().isUnauthorized()
                        .expectHeader().value(HttpHeaders.WWW_AUTHENTICATE,
                                value -> assertThat(value).contains("invalid_token")));
            }
        }

        @Test
        @DisplayName("a cookie-carrying POST without the CSRF token is refused (ADR-004)")
        void cookiePostNeedsCsrf() {
            run(withLogin(), client -> client.post().uri("/api/v1/echo")
                    .header(HttpHeaders.COOKIE, "OPENCRM_SESSION=abc")
                    .exchange()
                    .expectStatus().isForbidden());
        }

        @Test
        @DisplayName("health is public")
        void healthIsPublic() {
            run(withLogin(), client -> client.get().uri("/actuator/health").exchange().expectStatus().isOk());
        }
    }

    @Nested
    @DisplayName("jwt mode without a login (bearer lane only)")
    class WithoutLogin {

        @Test
        @DisplayName("anonymous calls get 401 without a login hint, pages included")
        void noLoginHint() {
            run(runner(), client -> {
                client.get().uri("/api/v1/echo").exchange().expectStatus().isUnauthorized()
                        .expectHeader().doesNotExist(GatewayAuthenticationEntryPoint.LOGIN_HINT_HEADER);
                client.get().uri("/quotes").accept(MediaType.TEXT_HTML).exchange().expectStatus().isUnauthorized();
                client.get().uri("/oauth2/authorization/entra").exchange().expectStatus().isUnauthorized();
            });
        }

        @Test
        @DisplayName("the production marker refuses a gateway without the browser login")
        void productionNeedsLogin() {
            runner().withPropertyValues("opencrm.env=prod").run(context -> assertThat(context).hasFailed()
                    .getFailure().rootCause().hasMessageContaining("opencrm.gateway.login.client-id is required"));
        }

        @Test
        @DisplayName("the production marker refuses a login client secret")
        void productionRefusesSecret() {
            withLogin().withPropertyValues("opencrm.env=prod").run(context -> assertThat(context).hasFailed()
                    .getFailure().rootCause().hasMessageContaining("client-secret is not allowed"));
        }
    }

    @Nested
    @DisplayName("dev mode")
    class DevMode {
        @Test
        @DisplayName("permits everything without a token; CSRF still applies to cookie POSTs")
        void permitsAll() {
            run(runner().withPropertyValues("spring.profiles.active=dev"), client -> {
                client.get().uri("/api/v1/echo").exchange().expectStatus().isOk();
                client.post().uri("/api/v1/echo").header(HttpHeaders.COOKIE, "OPENCRM_SESSION=abc").exchange()
                        .expectStatus().isForbidden();
            });
        }
    }
}
