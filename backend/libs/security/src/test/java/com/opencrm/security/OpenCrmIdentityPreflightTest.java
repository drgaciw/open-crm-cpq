package com.opencrm.security;

import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.OpenCrmSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-016 step 5: the go-live gate at startup")
class OpenCrmIdentityPreflightTest {

    /** A made-up API client id: v2.0 access tokens carry it as aud. */
    private static final String API_CLIENT_ID = "6f1c2d3e-4b5a-4c6d-8e7f-0a1b2c3d4e5f";

    private static OpenCrmSecurityProperties properties(String issuer, String... audiences) {
        OpenCrmSecurityProperties properties = new OpenCrmSecurityProperties();
        properties.getJwt().setIssuerUri(issuer);
        properties.getJwt().setAudiences(List.of(audiences));
        return properties;
    }

    private static MockEnvironment production() {
        return new MockEnvironment().withProperty("opencrm.env", "prod");
    }

    @Test
    @DisplayName("outside production it only reports, even with nothing configured")
    void reportsOutsideProduction() {
        String summary = OpenCrmIdentityPreflight.check(properties(null, "api://opencrm-api"), new MockEnvironment());

        assertThat(summary).contains("mode=jwt", "production=false", "issuer=none", "service identity=none");
    }

    @Test
    @DisplayName("production refuses a missing issuer, a missing API client id audience and a client secret, all at once")
    void productionListsEveryProblem() {
        OpenCrmSecurityProperties incomplete = properties(null, "api://opencrm-api");
        incomplete.getClient().setClientSecret("s3cret");

        assertThatThrownBy(() -> OpenCrmIdentityPreflight.check(incomplete, production()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADR-016 go-live gate")
                .hasMessageContaining("issuer-uri must be the tenant issuer")
                .hasMessageContaining("must include the API's client id")
                .hasMessageContaining("client-secret must not be set");
    }

    @Test
    @DisplayName("production refuses a multi-tenant issuer")
    void productionRefusesMultiTenantIssuer() {
        assertThatThrownBy(() -> OpenCrmIdentityPreflight.check(
                properties("https://login.microsoftonline.com/common/v2.0", API_CLIENT_ID), production()))
                .hasMessageContaining("tenant-specific");
    }

    @Test
    @DisplayName("production with the tenant issuer, the API client id and workload identity starts")
    void productionComplete() {
        MockEnvironment environment = production()
                .withProperty("AZURE_CLIENT_ID", "11111111-2222-4333-8444-555555555555")
                .withProperty("AZURE_FEDERATED_TOKEN_FILE", "/var/run/secrets/azure/tokens/azure-identity-token");

        String summary = OpenCrmIdentityPreflight.check(
                properties(TestTokens.ISSUER, API_CLIENT_ID, "api://opencrm-api"), environment);

        assertThat(summary).contains("production=true", "issuer=" + TestTokens.ISSUER,
                "service identity=11111111-2222-4333-8444-555555555555 (workload identity)");
    }

    private static final String SERVICE_ENDPOINTS = "health,info,prometheus";

    private static MockEnvironment productionExposing(String include) {
        return production().withProperty("management.endpoints.web.exposure.include", include)
                .withProperty("server.port", "8081");
    }

    private static OpenCrmSecurityProperties complete() {
        return properties(TestTokens.ISSUER, API_CLIENT_ID);
    }

    @Test
    @DisplayName("production refuses metrics on the service port and says how to give the actuator a port of its own")
    void productionRefusesMetricsOnTheServicePort() {
        assertThatThrownBy(() -> OpenCrmIdentityPreflight.check(complete(), productionExposing("health,info,metrics,prometheus")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exposes [metrics, prometheus] on the service port")
                .hasMessageContaining("MANAGEMENT_SERVER_PORT");
        assertThatThrownBy(() -> OpenCrmIdentityPreflight.check(complete(), productionExposing("*")))
                .hasMessageContaining("exposes [*] on the service port");
    }

    @Test
    @DisplayName("a management port equal to the service port is not a port of its own")
    void sameNumberIsTheSamePort() {
        MockEnvironment environment = productionExposing(SERVICE_ENDPOINTS).withProperty("management.server.port", "8081");

        assertThatThrownBy(() -> OpenCrmIdentityPreflight.check(complete(), environment))
                .hasMessageContaining("exposes [prometheus] on the service port");
    }

    @Test
    @DisplayName("production starts with a management port of its own, with the actuator off the network, or with health and info only")
    void productionAcceptsAnActuatorThatIsNotOnTheServicePort() {
        MockEnvironment separate = productionExposing("health,info,metrics,prometheus").withProperty("management.server.port", "9091");
        assertThat(OpenCrmIdentityPreflight.check(complete(), separate)).contains("management port=9091");

        MockEnvironment disabled = productionExposing("*").withProperty("management.server.port", "-1");
        assertThat(OpenCrmIdentityPreflight.check(complete(), disabled)).contains("management port=disabled");

        assertThat(OpenCrmIdentityPreflight.check(complete(), productionExposing("health,info"))).contains("management port=same");
        assertThat(OpenCrmIdentityPreflight.check(complete(), productionExposing(" Health , INFO "))).contains("production=true");
        assertThat(OpenCrmIdentityPreflight.check(complete(), production())).contains("production=true");
    }

    @Test
    @DisplayName("an excluded endpoint is not exposed")
    void excludedEndpointsAreNotExposed() {
        MockEnvironment environment = productionExposing(SERVICE_ENDPOINTS)
                .withProperty("management.endpoints.web.exposure.exclude", "prometheus");

        assertThat(OpenCrmIdentityPreflight.check(complete(), environment)).contains("production=true");
    }

    @Test
    @DisplayName("outside production the exposure is not checked")
    void exposureIsNotCheckedOutsideProduction() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.endpoints.web.exposure.include", "*");

        assertThat(OpenCrmIdentityPreflight.check(complete(), environment)).contains("production=false", "management port=same");
    }
    @Test
    @DisplayName("auto-configured: every service runs the gate when its context starts")
    void runsAtStartup() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenCrmPreflightAutoConfiguration.class))
                .withPropertyValues("opencrm.env=prod", "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER);

        runner.run(context -> assertThat(context).hasFailed().getFailure().rootCause()
                .hasMessageContaining("must include the API's client id"));
        runner.withPropertyValues("opencrm.security.jwt.audiences=" + API_CLIENT_ID + ",api://opencrm-api")
                .run(context -> assertThat(context).hasSingleBean(OpenCrmIdentityPreflight.Summary.class));
    }
}
