package com.opencrm.security.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-016 D7: Entra app roles map to ROLE_<UPPER_SNAKE>")
class EntraRoleAuthoritiesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "CPQ.User, ROLE_CPQ_USER",
            "DealDesk, ROLE_DEAL_DESK",
            "SalesVP, ROLE_SALES_VP",
            "VPSales, ROLE_VP_SALES",
            "CFO, ROLE_CFO",
            "Admin, ROLE_ADMIN",
            "Internal.Invoke, ROLE_INTERNAL_INVOKE",
            "InstalledBase.Read, ROLE_INSTALLED_BASE_READ",
            "deal-desk, ROLE_DEAL_DESK",
            "'  Sales VP ', ROLE_SALES_VP"
    })
    void appRoleToAuthority(String appRole, String authority) {
        assertThat(EntraRoleAuthorities.authorityFor(appRole)).isEqualTo(authority);
    }

    @Test
    @DisplayName("roles and delegated scopes both become authorities")
    void rolesAndScopes() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("alice")
                .claim("roles", List.of("CPQ.User", "DealDesk", " "))
                .claim("scp", "Quotes.Read")
                .issuedAt(Instant.now())
                .build();

        List<String> authorities = new EntraRoleAuthorities("roles").convert(jwt).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        assertThat(authorities).containsExactlyInAnyOrder("SCOPE_Quotes.Read", "ROLE_CPQ_USER", "ROLE_DEAL_DESK");
    }

    @Test
    @DisplayName("a token without roles has only its scopes")
    void noRoles() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "RS256").subject("svc").claim("scp", "a b").build();

        assertThat(new EntraRoleAuthorities("roles").convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("SCOPE_a", "SCOPE_b");
    }
}
