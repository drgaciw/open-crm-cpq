package com.opencrm.document.service;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClauseTenantTest {
    ClauseTenant tenant = new ClauseTenant();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void anonymousAndTenantlessUsersFailClosed() {
        assertThrows(BusinessException.class, tenant::requiredTenant);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "n/a", List.of()));
        assertThrows(BusinessException.class, tenant::requiredTenant);
    }
    @Test void verifiedJwtTenantAndActorAreUsed() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("subject")
                .claim("oid", "verified-user").claim("tid", "tenant-a").claim("scp", "documents").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        assertEquals("tenant-a", tenant.requiredTenant());
        assertEquals("verified-user", tenant.requiredActor());
    }
    @Test void applicationTokensAreNotHumanApprovers() {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "none").subject("app")
                .claim("azp", "client").claim("idtyp", "app").claim("tid", "tenant-a").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        assertThrows(BusinessException.class, tenant::requiredActor);
        assertThrows(BusinessException.class, tenant::requiredTenant);
    }
}
