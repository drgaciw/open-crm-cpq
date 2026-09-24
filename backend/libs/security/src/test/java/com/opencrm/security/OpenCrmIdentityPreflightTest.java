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
