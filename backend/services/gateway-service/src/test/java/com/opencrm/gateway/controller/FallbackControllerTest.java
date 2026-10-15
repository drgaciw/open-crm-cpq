package com.opencrm.gateway.controller;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A breaker forwards the original request, method included, so every breaker fallback must answer every
 * method the SPA and agents send with the 503 unavailable body, not 405 (DRG-528; quote/pricing since PR 581).
 */
@Tag("unit")
class FallbackControllerTest {

    private static final Map<String, String> BREAKER_FALLBACKS = Map.ofEntries(
            Map.entry("crm", "CRM Service"),
            Map.entry("pricing", "Pricing Service"),
            Map.entry("pricebook", "Price Book Service"),
            Map.entry("configuration", "Configuration Engine"),
            Map.entry("product", "Product Service"),
            Map.entry("quote", "Quote Service"),
            Map.entry("approval", "Approval Routing Service"),
            Map.entry("document", "Document Generation Service"),
            Map.entry("contract", "Contract/Asset Management Service"),
            Map.entry("communication", "Communication Service"),
            Map.entry("ai", "AI Service"));

    private static final List<HttpMethod> METHODS =
            List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    static Stream<Arguments> fallbacks() {
        return BREAKER_FALLBACKS.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> METHODS.stream().map(method -> Arguments.of(entry.getKey(), entry.getValue(), method)));
    }

    @ParameterizedTest(name = "{2} /fallback/{0}")
    @MethodSource("fallbacks")
    void everyBreakerFallbackAnswersUnavailableForEveryMethod(String path, String service, HttpMethod method) {
        WebTestClient.bindToController(new FallbackController()).build()
                .method(method).uri("/fallback/" + path).exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo(service + " is currently unavailable. Please try again later.")
                .jsonPath("$.timestamp").exists();
    }
}
