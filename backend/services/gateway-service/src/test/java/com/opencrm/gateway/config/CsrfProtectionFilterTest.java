package com.opencrm.gateway.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class CsrfProtectionFilterTest {

    private static final List<String> DEFAULT_ORIGINS = List.of(
            "http://localhost:4200",
            ""
    );

    @Test
    void getRequestsPassWithoutOriginCheck() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(DEFAULT_ORIGINS);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/deals").header(HttpHeaders.COOKIE, "session=abc").build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void stateChangingRequestWithoutCookiesPasses() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(DEFAULT_ORIGINS);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void allowedOriginPassesForCookieAuthenticatedPost() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(DEFAULT_ORIGINS);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.COOKIE, "session=abc")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void configuredFrontendUrlPassesWhenSet() {
        String frontendUrl = "https://opencrm-staging.azurewebsites.net";
        CsrfProtectionFilter filter = new CsrfProtectionFilter(List.of(
                "http://localhost:4200",
                frontendUrl
        ));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.COOKIE, "session=abc")
                        .header(HttpHeaders.ORIGIN, frontendUrl)
                        .build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }

    @Test
    void disallowedOriginReturnsForbidden() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(DEFAULT_ORIGINS);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.COOKIE, "session=abc")
                        .header(HttpHeaders.ORIGIN, "https://attacker.azurewebsites.net")
                        .build());

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void emptyFrontendUrlDoesNotAllowArbitraryOrigins() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(List.of("http://localhost:4200", ""));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.COOKIE, "session=abc")
                        .header(HttpHeaders.ORIGIN, "https://attacker.azurewebsites.net")
                        .build());

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void allowedRefererPassesWhenOriginMissing() {
        CsrfProtectionFilter filter = new CsrfProtectionFilter(DEFAULT_ORIGINS);
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = exchange -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/deals")
                        .header(HttpHeaders.COOKIE, "session=abc")
                        .header(HttpHeaders.REFERER, "http://localhost:4200/deals")
                        .build());

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
    }
}
