package com.opencrm.security.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** {@code dev} mode permits everything on both ports, as it does on the service port. */
@SpringBootTest(classes = AbstractManagementPortTest.App.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {AbstractManagementPortTest.EXPOSURE, "opencrm.security.mode=dev", "management.server.port=0"})
@DisplayName("Management port in dev mode: everything permitted")
class OpenCrmManagementDevModeTest extends AbstractManagementPortTest {

    @Test
    @DisplayName("the management port serves what is exposed, without a token")
    void devPermitsEverything() {
        assertThat(managementPort()).isNotEqualTo(servicePort());
        assertThat(get(managementPort(), "/actuator/prometheus", null).status()).isEqualTo(200);
        assertThat(get(managementPort(), "/actuator/metrics", null).status()).isEqualTo(200);
        assertThat(get(servicePort(), "/api/v1/ping", null).status()).isEqualTo(200);
    }
}