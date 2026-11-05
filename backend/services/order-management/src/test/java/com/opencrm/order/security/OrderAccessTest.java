package com.opencrm.order.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderAccessTest {
    private final OrderAccess access = new OrderAccess("pilot");
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    private void jwt(String scope, List<String> accounts, List<String> permissions) {
        var token = Jwt.withTokenValue("test").header("alg", "RS256").subject("operator")
            .claim("scope_id", scope).claim("account_ids", accounts).claim("permissions", permissions).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));
    }
    @Test void deniesMissingIdentityAndUntrustedAuthentication() {
        assertThrows(AccessDeniedException.class, () -> access.require("oms:read", "pilot", "a"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("operator", "x", List.of()));
        assertThrows(AccessDeniedException.class, () -> access.require("oms:read", "pilot", "a"));
    }
    @Test void allowsOnlyExplicitAccountPermissionAndDeployment() {
        jwt("pilot", List.of("a"), List.of("oms:read"));
        access.require("oms:read", "pilot", "a");
        assertEquals("operator", access.actor());
        assertEquals(java.util.Set.of("a"), access.customerIds());
        assertThrows(AccessDeniedException.class, () -> access.require("oms:cancel", "pilot", "a"));
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> access.require("oms:read", "pilot", "b")).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> access.require("oms:read", "other", "a")).getStatusCode().value());
    }
    @Test void deniesWrongIssuerDeploymentAndWildcardAccounts() {
        jwt("other", List.of("a"), List.of("oms:read"));
        assertThrows(AccessDeniedException.class, access::scopeId);
        jwt("pilot", List.of("*"), List.of("oms:read"));
        assertThrows(AccessDeniedException.class, access::customerIds);
    }
    @Test void cannotPromoteEvidencePermissionToFinance() {
        jwt("pilot", List.of("a"), List.of("oms:evidence"));
        assertThrows(AccessDeniedException.class, () -> access.require("oms:finance", "pilot", "a"));
    }
    @Test void blankDeploymentIsConfigurationError() {
        assertThrows(IllegalArgumentException.class, () -> new OrderAccess(" "));
    }
}
