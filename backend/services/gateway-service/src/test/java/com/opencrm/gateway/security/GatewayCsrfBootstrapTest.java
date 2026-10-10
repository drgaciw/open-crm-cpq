package com.opencrm.gateway.security;

import com.opencrm.gateway.config.CsrfProtectionFilter;
import com.opencrm.gateway.config.SecurityConfig;
import com.opencrm.gateway.controller.CsrfController;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

/** The bootstrap is public without making writes public or relaxing either CSRF check. */
@Tag("unit")
@DisplayName("SPA CSRF bootstrap remains public while token and origin checks protect writes")
class GatewayCsrfBootstrapTest {

    private static ReactiveWebApplicationContextRunner runner(boolean dev) {
        ReactiveWebApplicationContextRunner runner = new ReactiveWebApplicationContextRunner()
                .withUserConfiguration(GatewaySecurityChainTest.Echo.class, SecurityConfig.class,
                        GatewayLoginConfiguration.class, GatewaySessionConfiguration.class,
                        CsrfController.class, CsrfProtectionFilter.class)
                .withPropertyValues("opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER);
        return dev ? runner.withPropertyValues("spring.profiles.active=dev") : runner;
    }

    private static ResponseCookie bootstrap(WebTestClient client) {
        ResponseCookie cookie = client.get().uri("/api/v1/csrf").exchange()
                .expectStatus().isNoContent()
                .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
                .expectBody().isEmpty()
                .getResponseCookies().getFirst("XSRF-TOKEN");
        assertThat(cookie).as("a fresh browser receives the CSRF cookie").isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).as("the SPA can echo its token").isFalse();
        assertThat(cookie.getPath()).isEqualTo("/");
        return cookie;
    }

    @Test
    void anonymousBootstrapIsPublicInDefaultJwtMode() {
        runner(false).run(context -> {
            assertThat(context).hasNotFailed();
            bootstrap(WebTestClient.bindToApplicationContext(context).build());
        });
    }

    @Test
    void freshDevBrowserCanBootstrapAndEchoTheTokenForAWrite() {
        runner(true).run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            ResponseCookie cookie = bootstrap(client);
            client.post().uri("/api/v1/echo")
                    .cookie("XSRF-TOKEN", cookie.getValue())
                    .header("X-XSRF-TOKEN", cookie.getValue())
                    .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                    .exchange().expectStatus().isOk();
        });
    }

    @Test
    void bootstrapDoesNotPermitAWritingBrowserWithoutTheToken() {
        runner(true).run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            ResponseCookie cookie = bootstrap(client);
            client.post().uri("/api/v1/echo")
                    .cookie("XSRF-TOKEN", cookie.getValue())
                    .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                    .exchange().expectStatus().isForbidden();
        });
    }

    @Test
    void bootstrapDoesNotPermitAWrongToken() {
        runner(true).run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            ResponseCookie cookie = bootstrap(client);
            client.post().uri("/api/v1/echo")
                    .cookie("XSRF-TOKEN", cookie.getValue())
                    .header("X-XSRF-TOKEN", "wrong-token")
                    .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                    .exchange().expectStatus().isForbidden();
        });
    }

    @Test
    void bootstrapDoesNotPermitAWrongOriginWithAValidToken() {
        runner(true).run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            ResponseCookie cookie = bootstrap(client);
            client.post().uri("/api/v1/echo")
                    .cookie("XSRF-TOKEN", cookie.getValue())
                    .header("X-XSRF-TOKEN", cookie.getValue())
                    .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                    .exchange().expectStatus().isForbidden();
        });
    }

    @Test
    void publicBootstrapDoesNotExposeAdjacentRoutesOrUnsafeMethods() {
        runner(false).run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            ResponseCookie cookie = bootstrap(client);
            client.get().uri("/api/v1/csrf/extra").exchange().expectStatus().isUnauthorized();
            client.post().uri("/api/v1/csrf")
                    .cookie("XSRF-TOKEN", cookie.getValue())
                    .header("X-XSRF-TOKEN", cookie.getValue())
                    .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                    .exchange().expectStatus().isUnauthorized();
        });
    }
}
