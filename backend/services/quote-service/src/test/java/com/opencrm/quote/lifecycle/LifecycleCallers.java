package com.opencrm.quote.lifecycle;

import com.opencrm.security.test.TestTokens;
import com.opencrm.security.web.EntraRoleAuthorities;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

/** Entra-shaped caller with an explicit {@code tid} claim. {@code TestCallers} does not set one. */
final class LifecycleCallers {

    private LifecycleCallers() {
    }

    /** A person. Returns the {@code oid}. {@code tid} is the tenant the service must record. */
    static String signIn(String name, String tid, String... appRoles) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(TestTokens.ISSUER)
                .subject(name)
                .audience(List.of(TestTokens.AUDIENCE))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .claim("oid", TestTokens.oidOf(name))
                .claim("azp", TestTokens.USER_CLIENT_ID)
                .claim("scp", TestTokens.USER_SCOPE)
                .claim("tid", tid);
        if (appRoles.length > 0) {
            jwt.claim("roles", List.of(appRoles));
        }
        Jwt token = jwt.build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(token, new EntraRoleAuthorities("roles").convert(token)));
        return token.getClaimAsString("oid");
    }
}
