package com.opencrm.gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Agentic UAT: browser {@code POST /api/v1/organizations} via gateway returned
 * {@code 403 Access Denied} because Spring Security CSRF was enabled in {@code dev}
 * without ever issuing an {@code XSRF-TOKEN} cookie. Local {@code nx serve crm}
 * creates must work without Azure AD / CSRF token plumbing — Origin checks remain
 * in {@link CsrfProtectionFilter} when cookies are present.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SecurityConfigDevProfileTest.TestApp.class)
@ActiveProfiles("dev")
@DisplayName("gateway SecurityConfig dev profile")
class SecurityConfigDevProfileTest {

    @Configuration
    @EnableWebFlux
    @Import(SecurityConfig.class)
    static class TestApp {
        @Bean
        RouterFunction<ServerResponse> probeRoutes() {
            return route(POST("/api/v1/organizations"),
                    request -> ServerResponse.ok().bodyValue("created"));
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    @DisplayName("unauthenticated POST without CSRF token is permitted in dev")
    void unauthenticatedPostWithoutCsrfTokenIsPermitted() {
        webTestClient.post()
                .uri("/api/v1/organizations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("created");
    }
}
