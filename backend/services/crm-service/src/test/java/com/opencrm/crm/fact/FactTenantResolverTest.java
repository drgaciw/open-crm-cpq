package com.opencrm.crm.fact;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.crm.fields.FixtureTenants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FactTenantResolverTest {

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedTidIsUsedWhenQueryTenantIsAbsent() {
        signIn(FixtureTenants.A);

        assertThat(FactTenantResolver.requireTenant(null)).isEqualTo(FixtureTenants.A);
        assertThat(FactTenantResolver.requireTenant("  ")).isEqualTo(FixtureTenants.A);
    }

    @Test
    void matchingQueryTenantIsAllowed() {
        signIn(FixtureTenants.A);

        assertThat(FactTenantResolver.requireTenant(FixtureTenants.A)).isEqualTo(FixtureTenants.A);
    }

    @Test
    void spoofedQueryTenantCannotEscalate() {
        signIn(FixtureTenants.A);

        assertThatThrownBy(() -> FactTenantResolver.requireTenant(FixtureTenants.B))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TENANT_MISMATCH");
    }

    @Test
    void missingTidFailsClosed() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("person-oid")
                .claim("oid", "person-oid")
                .claim("scp", "user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThatThrownBy(() -> FactTenantResolver.requireTenant(null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("TENANT_REQUIRED");
    }

    @Test
    void serviceTokenTidIsAccepted() {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("enrichment-agent")
                .claim("idtyp", "app")
                .claim("azp", "enrichment-agent")
                .claim("tid", FixtureTenants.A)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(FactTenantResolver.requireTenant(null)).isEqualTo(FixtureTenants.A);
    }

    private static void signIn(String tid) {
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "none")
                .subject("person-oid")
                .claim("oid", "person-oid")
                .claim("scp", "user")
                .claim("tid", tid)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }
}
