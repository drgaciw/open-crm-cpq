package com.opencrm.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker-free CSRF origin allow-list tests: wildcards rejected; localhost allowed.
 */
@Tag("unit")
class CsrfProtectionFilterTest {

    private static final Path APPLICATION_YAML = Path.of("src/main/resources/application.yml");

    @Test
    @DisplayName("constructor rejects *.azurewebsites.net wildcard in allow-list")
    void constructorRejectsAzureWildcardPattern() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new CsrfProtectionFilter(List.of(
                        "http://localhost:4200",
                        "https://*.azurewebsites.net")));
        assertTrue(ex.getMessage().contains("Wildcard"));
        assertTrue(ex.getMessage().contains("*.azurewebsites.net"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://*.azurewebsites.net",
            "http://*.example.com",
            "*",
            "https://app.*.com"
    })
    @DisplayName("sanitizeAllowedOrigins rejects any origin containing *")
    void sanitizeRejectsWildcardPatterns(String wildcardOrigin) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> CsrfProtectionFilter.sanitizeAllowedOrigins(List.of(wildcardOrigin)));
        assertTrue(ex.getMessage().contains("Wildcard"));
    }

    @Test
    @DisplayName("sanitizeAllowedOrigins requires at least one explicit origin")
    void sanitizeRejectsEmptyAllowList() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CsrfProtectionFilter.sanitizeAllowedOrigins(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> CsrfProtectionFilter.sanitizeAllowedOrigins(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> CsrfProtectionFilter.sanitizeAllowedOrigins(List.of("  ", "")));
    }

    @Test
    @DisplayName("POST with cookie from unlisted Azure subdomain is forbidden")
    void rejectsUnlistedAzureSubdomainOrigin() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(List.of("http://localhost:4200"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/leads")
                        .header(HttpHeaders.COOKIE, "SESSION=abc")
                        .header(HttpHeaders.ORIGIN, "https://evil.azurewebsites.net")
                        .build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertFalse(chainInvoked.get());
        assertTrue(exchange.getResponse().getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("wildcard Origin header does not match exact allow-list entry")
    void rejectsLiteralWildcardOriginHeader() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(List.of("http://localhost:4200"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/leads")
                        .header(HttpHeaders.COOKIE, "SESSION=abc")
                        .header(HttpHeaders.ORIGIN, "https://*.azurewebsites.net")
                        .build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertFalse(chainInvoked.get());
        assertTrue(exchange.getResponse().getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("POST with cookie from http://localhost:4200 is allowed")
    void allowsLocalhostFrontendOrigin() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(List.of("http://localhost:4200"));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/leads")
                        .header(HttpHeaders.COOKIE, "SESSION=abc")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .build());

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertTrue(chainInvoked.get());
        assertTrue(exchange.getResponse().getStatusCode() == null
                || !exchange.getResponse().getStatusCode().isError());
    }

    @Test
    @DisplayName("explicit FRONTEND_URL-style origin is allowed; sibling subdomain is not")
    void allowsExplicitOriginOnly() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(
                List.of("https://crm-portal.azurewebsites.net"));

        MockServerWebExchange allowed = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.COOKIE, "SESSION=abc")
                        .header(HttpHeaders.ORIGIN, "https://crm-portal.azurewebsites.net")
                        .build());
        AtomicBoolean allowedChain = new AtomicBoolean(false);
        filter.filter(allowed, ex -> {
            allowedChain.set(true);
            return Mono.empty();
        }).block();
        assertTrue(allowedChain.get());

        MockServerWebExchange sibling = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.COOKIE, "SESSION=abc")
                        .header(HttpHeaders.ORIGIN, "https://other-app.azurewebsites.net")
                        .build());
        AtomicBoolean siblingChain = new AtomicBoolean(false);
        filter.filter(sibling, ex -> {
            siblingChain.set(true);
            return Mono.empty();
        }).block();
        assertFalse(siblingChain.get());
        assertTrue(sibling.getResponse().getStatusCode() == HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("safe methods and cookieless requests skip CSRF origin check")
    void skipsWhenNoCookieOrSafeMethod() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(List.of("http://localhost:4200"));

        MockServerWebExchange get = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.GET, "/api/v1/leads")
                        .header(HttpHeaders.COOKIE, "SESSION=abc")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .build());
        AtomicBoolean getChain = new AtomicBoolean(false);
        filter.filter(get, ex -> {
            getChain.set(true);
            return Mono.empty();
        }).block();
        assertTrue(getChain.get());

        MockServerWebExchange noCookie = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/leads")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .build());
        AtomicBoolean postChain = new AtomicBoolean(false);
        filter.filter(noCookie, ex -> {
            postChain.set(true);
            return Mono.empty();
        }).block();
        assertTrue(postChain.get());
    }

    @Test
    @DisplayName("application.yml has no Azure CORS/CSRF wildcard origins")
    void applicationYamlHasNoAzureWildcards() throws Exception {
        String yaml = Files.readString(APPLICATION_YAML);
        assertFalse(yaml.contains("*.azurewebsites.net"),
                "gateway application.yml must not list https://*.azurewebsites.net");
        assertTrue(yaml.contains("FRONTEND_URL") || yaml.contains("CORS_ALLOWED_ORIGINS"),
                "gateway CORS/CSRF origins must be env-driven");
        assertTrue(yaml.contains("http://localhost:4200"),
                "local default http://localhost:4200 must remain");
    }
}
