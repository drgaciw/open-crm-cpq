package com.opencrm.security.web;

import com.opencrm.security.web.OpenCrmSecurityProperties.SecurityMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-016 D4: opencrm.security.mode and the production-marker guards")
class OpenCrmSecurityModeTest {

    private final OpenCrmSecurityProperties properties = new OpenCrmSecurityProperties();
    private final MockEnvironment environment = new MockEnvironment();

    @Test
    @DisplayName("no profile and no mode: jwt")
    void defaultIsJwt() {
        assertThat(OpenCrmSecurityAutoConfiguration.mode(properties, environment)).isEqualTo(SecurityMode.JWT);
    }

    @Test
    @DisplayName("the dev profile alone: dev")
    void devProfileIsDev() {
        environment.setActiveProfiles("dev");

        assertThat(OpenCrmSecurityAutoConfiguration.mode(properties, environment)).isEqualTo(SecurityMode.DEV);
    }

    @Test
    @DisplayName("other profiles (test, docker, uat) stay jwt")
    void otherProfilesAreJwt() {
        environment.setActiveProfiles("test", "docker", "uat");

        assertThat(OpenCrmSecurityAutoConfiguration.mode(properties, environment)).isEqualTo(SecurityMode.JWT);
    }

    @Test
    @DisplayName("an explicit mode=jwt wins over the dev profile, so dev can run against a test issuer")
    void explicitJwtWins() {
        environment.setActiveProfiles("dev");
        properties.setMode(SecurityMode.JWT);

        assertThat(OpenCrmSecurityAutoConfiguration.mode(properties, environment)).isEqualTo(SecurityMode.JWT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production", " PROD "})
    @DisplayName("dev mode with the production marker refuses to start")
    void devRefusedInProduction(String marker) {
        environment.setProperty("opencrm.env", marker);
        properties.setMode(SecurityMode.DEV);

        assertThatThrownBy(() -> OpenCrmSecurityAutoConfiguration.mode(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not allowed");
    }

    @Test
    @DisplayName("the dev profile with the production marker refuses to start")
    void devProfileRefusedInProduction() {
        environment.setActiveProfiles("dev");
        environment.setProperty("opencrm.env", "prod");

        assertThatThrownBy(() -> OpenCrmSecurityAutoConfiguration.mode(properties, environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("non-production markers (uat, staging) do not block dev mode")
    void nonProductionMarker() {
        environment.setProperty("opencrm.env", "uat");
        properties.setMode(SecurityMode.DEV);

        assertThat(OpenCrmSecurityAutoConfiguration.mode(properties, environment)).isEqualTo(SecurityMode.DEV);
    }

    @Test
    @DisplayName("legacy-open-internal is off by default, allowed outside production, refused in production")
    void legacyOpenInternalGuard() {
        assertThat(OpenCrmSecurityAutoConfiguration.legacyOpenInternal(properties, environment)).isFalse();

        properties.setLegacyOpenInternal(true);
        assertThat(OpenCrmSecurityAutoConfiguration.legacyOpenInternal(properties, environment)).isTrue();

        environment.setProperty("opencrm.env", "production");
        assertThatThrownBy(() -> OpenCrmSecurityAutoConfiguration.legacyOpenInternal(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("never permitAll");
    }

    @Test
    @DisplayName("defaults: audience api://opencrm-api, roles claim, 60 s skew, Internal.Invoke, API docs private")
    void defaults() {
        assertThat(properties.getJwt().getAudiences()).containsExactly("api://opencrm-api");
        assertThat(properties.getJwt().getRolesClaim()).isEqualTo("roles");
        assertThat(properties.getJwt().getClockSkew()).hasSeconds(60);
        assertThat(properties.getInternalRole()).isEqualTo("Internal.Invoke");
        assertThat(properties.isPermitApiDocs()).isFalse();
    }
}
