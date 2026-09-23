package com.opencrm.security.web;

import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-016 D1: tenant issuer, validated audience, clock skew")
class OpenCrmJwtDecodersTest {

    private static OpenCrmSecurityProperties.Jwt tenant() {
        OpenCrmSecurityProperties.Jwt jwt = new OpenCrmSecurityProperties.Jwt();
        jwt.setIssuerUri(TestTokens.ISSUER);
        return jwt;
    }

    private static JwtDecoder testKeyDecoder(OpenCrmSecurityProperties.Jwt properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestTokens.publicKey()).build();
        decoder.setJwtValidator(OpenCrmJwtDecoders.validator(properties));
        return decoder;
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://login.microsoftonline.com/common/v2.0",
            "https://login.microsoftonline.com/organizations/v2.0",
            "https://login.microsoftonline.com/consumers/v2.0",
            "https://login.microsoftonline.com/Common"
    })
    @DisplayName("multi-tenant issuers are refused at startup")
    void multiTenantIssuerRefused(String issuer) {
        OpenCrmSecurityProperties.Jwt jwt = tenant();
        jwt.setIssuerUri(issuer);

        assertThatThrownBy(() -> OpenCrmJwtDecoders.create(jwt, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant-specific");
    }

    @Test
    @DisplayName("an empty audience list is refused at startup")
    void emptyAudienceRefused() {
        OpenCrmSecurityProperties.Jwt jwt = tenant();
        jwt.setAudiences(List.of(" "));

        assertThatThrownBy(() -> OpenCrmJwtDecoders.create(jwt, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("audiences");
    }

    @Test
    @DisplayName("a tenant issuer builds a lazy decoder: no call to the identity provider at startup")
    void tenantIssuerIsLazy() {
        OpenCrmSecurityProperties.Jwt jwt = tenant();
        jwt.setIssuerUri("https://login.microsoftonline.com/00000000-0000-4000-8000-00000000c0de/v2.0");

        assertThat(OpenCrmJwtDecoders.create(jwt, true)).isNotNull();
    }

    @Test
    @DisplayName("no issuer outside production: starts, and rejects every token")
    void unconfiguredRejectsTokens() {
        JwtDecoder decoder = OpenCrmJwtDecoders.create(new OpenCrmSecurityProperties.Jwt(), false);

        assertThatThrownBy(() -> decoder.decode(TestTokens.user("alice", "CPQ.User").mint()))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("issuer-uri");
    }

    @Test
    @DisplayName("no issuer with the production marker: refuses to start")
    void unconfiguredFailsInProduction() {
        assertThatThrownBy(() -> OpenCrmJwtDecoders.create(new OpenCrmSecurityProperties.Jwt(), true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("production marker");
    }

    @Test
    @DisplayName("a valid token decodes, with its subject and roles")
    void validTokenDecodes() {
        var jwt = testKeyDecoder(tenant()).decode(TestTokens.user("alice", "CPQ.User").mint());

        assertThat(jwt.getSubject()).isEqualTo("alice");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CPQ.User");
    }

    @Test
    @DisplayName("wrong audience, wrong issuer and expiry beyond the skew are rejected")
    void validatorRejects() {
        JwtDecoder decoder = testKeyDecoder(tenant());

        assertThatThrownBy(() -> decoder.decode(TestTokens.user("a").audience("api://other").mint()))
                .isInstanceOf(JwtValidationException.class).hasMessageContaining("aud");
        assertThatThrownBy(() -> decoder.decode(TestTokens.user("a")
                .issuer("https://login.microsoftonline.com/11111111-1111-4111-8111-111111111111/v2.0").mint()))
                .isInstanceOf(JwtValidationException.class).hasMessageContaining("iss");
        assertThatThrownBy(() -> decoder.decode(TestTokens.user("a").expiresAt(Instant.now().minusSeconds(120)).mint()))
                .isInstanceOf(JwtValidationException.class).hasMessageContaining("expired");
    }

    @Test
    @DisplayName("expiry within the 60 s clock skew is tolerated")
    void skewTolerated() {
        String token = TestTokens.user("alice").expiresAt(Instant.now().minusSeconds(20)).mint();

        assertThat(testKeyDecoder(tenant()).decode(token).getSubject()).isEqualTo("alice");
    }

    @Test
    @DisplayName("a second accepted audience is honoured")
    void additionalAudience() {
        OpenCrmSecurityProperties.Jwt jwt = tenant();
        jwt.setAudiences(List.of("api://opencrm-api", "api://opencrm-internal"));

        assertThat(testKeyDecoder(jwt).decode(TestTokens.user("a").audience("api://opencrm-internal").mint()))
                .isNotNull();
    }
}
