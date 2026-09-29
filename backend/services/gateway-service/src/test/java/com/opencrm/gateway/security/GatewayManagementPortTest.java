package com.opencrm.gateway.security;

import com.opencrm.gateway.GatewayApplication;
import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmJwtDecoders;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owner decision 4 at the gateway, which is reactive and so has a chain of its own: with a management port of its own,
 * Prometheus scrapes {@code /actuator/prometheus} without a credential, and nothing else is reachable there. The
 * whole application runs on real ports; Redis is not reachable, which only makes readiness report {@code DOWN}.
 */
@Tag("unit")
@DisplayName("Gateway management port: metrics scraped without a token, nothing else opened")
@SpringBootTest(classes = GatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER, "management.server.port=0",
                "management.endpoints.web.exposure.include=health,info,gateway,prometheus",
                "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=1"})
@Import(GatewayManagementPortTest.TestKey.class)
class GatewayManagementPortTest {

    /** Tokens of the test issuer, so that the service port can be asked with a valid one. */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestKey {
        @Bean
        @Primary
        ReactiveJwtDecoder testKeyDecoder(OpenCrmSecurityProperties properties) {
            NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
            decoder.setJwtValidator(OpenCrmJwtDecoders.validator(properties.getJwt()));
            return decoder;
        }
    }

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("the actuator runs on a port of its own")
    void separatePort() {
        assertThat(managementPort()).isNotEqualTo(servicePort());
    }

    @Test
    @DisplayName("health, info and the Prometheus scrape are served without a token")
    void scrapeEndpointsArePublic() {
        assertThat(get(managementPort(), "/actuator/health", null)).isNotIn(401, 403, 404);
        assertThat(get(managementPort(), "/actuator/info", null)).isEqualTo(200);
        assertThat(get(managementPort(), "/actuator/prometheus", null)).isEqualTo(200);
    }

    @Test
    @DisplayName("every other endpoint and path is refused on the management port, with or without a token")
    void everythingElseIsRefused() {
        String token = TestTokens.user("alice", "CPQ.User").bearer();
        for (String path : new String[] {"/actuator", "/actuator/metrics", "/actuator/env", "/actuator/gateway/routes",
                "/api/v1/ai/agents", "/"}) {
            assertThat(get(managementPort(), path, null)).as("anonymous %s", path).isEqualTo(403);
            assertThat(get(managementPort(), path, token)).as("user token %s", path).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("the service port still needs a token, and it does not serve the actuator")
    void servicePortIsUnchanged() {
        String token = TestTokens.user("alice", "CPQ.User").bearer();
        assertThat(get(servicePort(), "/api/v1/ai/agents", null)).isEqualTo(401);
        assertThat(get(servicePort(), "/actuator/prometheus", null)).isEqualTo(401);
        assertThat(get(servicePort(), "/actuator/prometheus", token)).isEqualTo(404);
        assertThat(get(servicePort(), "/actuator/gateway/routes", token)).isEqualTo(404);
    }

    private String servicePort() {
        return environment.getProperty("local.server.port");
    }

    private String managementPort() {
        return environment.getProperty("local.management.port", servicePort());
    }

    private static int get(String port, String path, String authorization) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<Void> response = client.send(request.build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}