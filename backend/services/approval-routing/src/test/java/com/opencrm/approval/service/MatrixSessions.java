package com.opencrm.approval.service;

import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.EntraRoleAuthorities;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

/** Puts a caller with an Entra {@code tid} into the security context. */
final class MatrixSessions {

    static final String TENANT_A = "tenant-a";
    static final String TENANT_B = "tenant-b";

    private MatrixSessions() {
    }

    static String user(String name, String tenantId, String... roles) {
        Jwt jwt = Jwt.withTokenValue("matrix-test")
                .header("alg", "none")
                .subject(name)
                .claim("oid", TestTokens.oidOf(name))
                .claim("tid", tenantId)
                .claim("azp", TestTokens.USER_CLIENT_ID)
                .claim("scp", TestTokens.USER_SCOPE)
                .claim("roles", List.of(roles))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, new EntraRoleAuthorities("roles").convert(jwt)));
        return TestTokens.oidOf(name);
    }

    static void service(String clientId) {
        Jwt jwt = Jwt.withTokenValue("matrix-test")
                .header("alg", "none")
                .subject(clientId)
                .claim("oid", TestTokens.oidOf("service:" + clientId))
                .claim("tid", TENANT_A)
                .claim("azp", clientId)
                .claim("idtyp", "app")
                .claim("roles", List.of("Internal.Invoke"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, new EntraRoleAuthorities("roles").convert(jwt)));
    }

    static void clear() {
        SecurityContextHolder.clearContext();
    }
}
