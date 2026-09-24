package com.opencrm.gateway.security;

import com.opencrm.gateway.config.SecurityConfig;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-004 with ADR-016 step 5: the SPA can only send {@code X-XSRF-TOKEN} if the gateway hands it the
 * {@code XSRF-TOKEN} cookie. WebFlux defers the CSRF token and writes the cookie only when something subscribes to it.
 * Before step 5 nothing did: this test failed, and every cookie-authenticated state change from the SPA, sign-out
 * included, would have been refused with 403.
 */
@Tag("unit")
@DisplayName("ADR-004/016: the SPA receives the XSRF-TOKEN cookie and can use it")
class GatewayCsrfCookieTest {

    private static ReactiveWebApplicationContextRunner runner() {
        return new ReactiveWebApplicationContextRunner()
                .withUserConfiguration(GatewaySecurityChainTest.Echo.class, SecurityConfig.class,
                        GatewayLoginConfiguration.class, GatewaySessionConfiguration.class)
                .withPropertyValues("opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER,
                        "opencrm.gateway.login.client-id=gateway-client",
                        "opencrm.gateway.login.client-secret=local-test-secret");
    }

    private static ResponseCookie xsrfCookie(WebTestClient client, String path) {
        ResponseCookie xsrf = client.get().uri(path).exchange()
                .returnResult(String.class).getResponseCookies().getFirst("XSRF-TOKEN");
        assertThat(xsrf).as("XSRF-TOKEN cookie on a plain GET of " + path).isNotNull();
        assertThat(xsrf.isHttpOnly()).as("the SPA must be able to read it").isFalse();
        return xsrf;
    }

    @Test
    @DisplayName("a GET hands out XSRF-TOKEN, readable by script, and a cookie POST echoing it passes CSRF")
    void spaGetsAndUsesTheCsrfCookie() {
        runner().withPropertyValues("spring.profiles.active=dev").run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            ResponseCookie xsrf = xsrfCookie(client, "/api/v1/echo");

            client.post().uri("/api/v1/echo")
                    .cookie("OPENCRM_SESSION", "abc")
                    .cookie("XSRF-TOKEN", xsrf.getValue())
                    .header("X-XSRF-TOKEN", xsrf.getValue())
                    .exchange()
                    .expectStatus().isOk();
        });
    }

    @Test
    @DisplayName("jwt mode: an anonymous request gets the cookie too, and sign-out with it lands on the app")
    void signOutWithTheCookie() {
        runner().run(context -> {
            WebTestClient client = WebTestClient.bindToApplicationContext(context).build();
            ResponseCookie xsrf = xsrfCookie(client, "/actuator/health");

            client.post().uri("/logout").cookie("OPENCRM_SESSION", "abc").exchange()
                    .expectStatus().isForbidden();
            // The SPA signs out with a form post (a page navigation, so the browser follows the redirect to Entra's
            // end-session endpoint), carrying the token as the _csrf field.
            client.post().uri("/logout")
                    .cookie("OPENCRM_SESSION", "abc")
                    .cookie("XSRF-TOKEN", xsrf.getValue())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("_csrf", xsrf.getValue()))
                    .exchange()
                    .expectStatus().isFound()
                    .expectHeader().location("/");
        });
    }
}
