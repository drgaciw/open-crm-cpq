package com.opencrm.security.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Owner decision 4: with a management port of its own, Prometheus scrapes {@code /actuator/prometheus} without a
 * credential, and nothing else that the actuator or the service offers is reachable there. Metrics are not served on
 * the service port at all, so they cannot leave through an ingress or the gateway.
 */
@SpringBootTest(classes = AbstractManagementPortTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {AbstractManagementPortTest.ISSUER, AbstractManagementPortTest.EXPOSURE,
                "management.server.port=0"})
@DisplayName("Management port: metrics scraped without a token, nothing else opened")
class OpenCrmManagementPortTest extends AbstractManagementPortTest {

    @Test
    @DisplayName("the actuator runs on a port of its own")
    void separatePort() {
        assertThat(managementPort()).isNotEqualTo(servicePort());
    }

    @Test
    @DisplayName("health, readiness, info and the Prometheus scrape are served without a token")
    void scrapeEndpointsArePublic() {
        assertThat(get(managementPort(), "/actuator/health", null).status()).isEqualTo(200);
        assertThat(get(managementPort(), "/actuator/health/readiness", null).status()).isEqualTo(200);
        assertThat(get(managementPort(), "/actuator/info", null).status()).isEqualTo(200);
        Response scrape = get(managementPort(), "/actuator/prometheus", null);
        assertThat(scrape.status()).isEqualTo(200);
        assertThat(scrape.body()).contains("# HELP").contains("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("every other endpoint and every API path is refused on the management port, with or without a token")
    void everythingElseIsRefused() {
        for (String path : new String[] {"/actuator", "/actuator/metrics", "/actuator/env", "/actuator/beans",
                "/api/v1/ping", "/internal/v1/anything", "/"}) {
            assertThat(get(managementPort(), path, null).status()).as("anonymous %s", path).isEqualTo(403);
            assertThat(get(managementPort(), path, userToken()).status()).as("user token %s", path).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("the service port still needs a token, and it does not serve the actuator's metrics")
    void servicePortIsUnchanged() {
        assertThat(get(servicePort(), "/api/v1/ping", null).status()).isEqualTo(401);
        assertThat(get(servicePort(), "/api/v1/ping", userToken()).status()).isEqualTo(200);
        assertThat(get(servicePort(), "/actuator/prometheus", null).status()).isEqualTo(401);
        assertThat(get(servicePort(), "/actuator/prometheus", userToken()).status()).isEqualTo(404);
        assertThat(get(servicePort(), "/actuator/metrics", userToken()).status()).isEqualTo(404);
    }
}