package com.opencrm.security.test;

import com.opencrm.security.web.EntraRoleAuthorities;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

/**
 * Puts an Entra-shaped caller into the security context, for unit tests of code that records actors or checks roles
 * without going through the filter chain. The token has the same claims as {@link TestTokens} mints, and the
 * authorities come from the production converter ({@link EntraRoleAuthorities}).
 *
 * <pre>{@code
 * TestCallers.signInUser("alice", "DealDesk");   // actor TestTokens.oidOf("alice"), ROLE_DEAL_DESK
 * TestCallers.signInService("quote-service");    // actor service:quote-service
 * TestCallers.clear();
 * }</pre>
 */
public final class TestCallers {

    private TestCallers() {
    }

    /** A person with the given Entra app roles; returns their actor id, {@link TestTokens#oidOf(String)}. */
    public static String signInUser(String name, String... appRoles) {
        Jwt.Builder jwt = base(name, appRoles)
                .claim("oid", TestTokens.oidOf(name))
                .claim("azp", TestTokens.USER_CLIENT_ID)
                .claim("scp", TestTokens.USER_SCOPE);
        return signIn(jwt.build());
    }

    /** An application with a client-credentials token; returns its actor id, {@code service:<clientId>}. */
    public static String signInService(String clientId, String... appRoles) {
        String servicePrincipal = TestTokens.oidOf("service:" + clientId);
        Jwt.Builder jwt = base(servicePrincipal, appRoles)
                .claim("oid", servicePrincipal)
                .claim("azp", clientId)
                .claim("idtyp", "app");
        signIn(jwt.build());
        return "service:" + clientId;
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt.Builder base(String subject, String... appRoles) {
        Jwt.Builder jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(TestTokens.ISSUER)
                .subject(subject)
                .audience(List.of(TestTokens.AUDIENCE))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600));
        if (appRoles.length > 0) {
            jwt.claim("roles", List.of(appRoles));
        }
        return jwt;
    }

    private static String signIn(Jwt jwt) {
        JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(jwt, new EntraRoleAuthorities("roles").convert(jwt));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return jwt.getClaimAsString("oid");
    }
}
