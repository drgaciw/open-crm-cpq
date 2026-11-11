package com.opencrm.gateway.config;

import com.opencrm.gateway.GatewayApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The packaged {@code application.yml} supplies {@code security.csrf.allowed-origins} as one comma-separated
 * string. {@link OriginConfigurationTest} checks that string; this checks what the running filter does with it:
 * every listed origin is allowed as its own entry and nothing else is.
 *
 * <p>The application starts through {@code SpringApplication}, as it does in production, because that is what
 * installs the conversion service that splits the string. A bare context runner does not, and keeps the whole
 * value as a single origin. Redis is not reachable, which only makes readiness report {@code DOWN}.
 */
@Tag("unit")
@DisplayName("CSRF allowed origins bind from the packaged configuration as a list")
class CsrfOriginBindingTest {

    /** A cookie-carrying write from {@code origin}: true when the filter lets it through. */
    private static boolean allows(CsrfProtectionFilter filter, String origin) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/leads")
                        .header(HttpHeaders.COOKIE, "SESSION=abc")
                        .header(HttpHeaders.ORIGIN, origin)
                        .build());
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        filter.filter(exchange, ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        }).block();
        if (!chainInvoked.get()) {
            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        return chainInvoked.get();
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"management.server.port=0", "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
    class PackagedDefaults {

        @Autowired
        private CsrfProtectionFilter filter;

        @Test
        void bothLoopbackOriginsAreAllowedAndNothingElse() {
            assertThat(allows(filter, "http://localhost:4200")).isTrue();
            assertThat(allows(filter, "http://127.0.0.1:4200")).isTrue();
            assertThat(allows(filter, "https://untrusted.example")).isFalse();
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"management.server.port=0", "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1",
                    "CORS_ALLOWED_ORIGINS=https://app.example,https://admin.example"})
    class ExplicitOriginList {

        @Autowired
        private CsrfProtectionFilter filter;

        @Test
        void everyListedOriginIsAllowedAndTheDefaultsAreNot() {
            assertThat(allows(filter, "https://app.example")).isTrue();
            assertThat(allows(filter, "https://admin.example")).isTrue();
            assertThat(allows(filter, "http://localhost:4200")).isFalse();
        }
    }
}
