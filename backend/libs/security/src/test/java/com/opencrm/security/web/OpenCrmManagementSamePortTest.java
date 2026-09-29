package com.opencrm.security.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without {@code management.server.port} the actuator shares the service port and only the baseline chain applies:
 * health is public, and metrics need a token. This is what a service does until the management port is set.
 */
@SpringBootTest(classes = AbstractManagementPortTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {AbstractManagementPortTest.ISSUER, AbstractManagementPortTest.EXPOSURE})
@DisplayName("Management on the service port: the baseline chain, metrics need a token")
class OpenCrmManagementSamePortTest extends AbstractManagementPortTest {

    @Test
    @DisplayName("health is public; the Prometheus scrape and metrics are not")
    void metricsNeedAToken() {
        assertThat(managementPort()).isEqualTo(servicePort());
        assertThat(get(managementPort(), "/actuator/health", null).status()).isEqualTo(200);
        assertThat(get(managementPort(), "/actuator/prometheus", null).status()).isEqualTo(401);
        assertThat(get(managementPort(), "/actuator/metrics", null).status()).isEqualTo(401);
    }

    @Test
    @DisplayName("with a token the scrape is served: there is no role for it, which is why production wants the management port")
    void metricsWithATokenAreServed() {
        assertThat(get(managementPort(), "/actuator/prometheus", userToken()).status()).isEqualTo(200);
    }
}