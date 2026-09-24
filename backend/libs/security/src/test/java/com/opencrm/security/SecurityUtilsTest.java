package com.opencrm.security;

import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SecurityUtils: who is calling (ADR-016 decision 6)")
class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("t").header("alg", "none")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    private static void authenticate(Jwt jwt, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList(authorities)));
    }

    @Test
    @DisplayName("an anonymous caller is not a caller")
    void anonymousIsNotACaller() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(SecurityUtils.isAuthenticated()).isFalse();
        assertThat(SecurityUtils.getCurrentCaller()).isEmpty();
        assertThat(SecurityUtils.getCurrentUser()).isEmpty();
        assertThatThrownBy(SecurityUtils::getCurrentUserId).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("no authentication at all is not a caller")
    void emptyContextIsNotACaller() {
        assertThat(SecurityUtils.isAuthenticated()).isFalse();
        assertThat(SecurityUtils.getCurrentCaller()).isEmpty();
        assertThat(SecurityUtils.getCurrentUser()).isEmpty();
    }

    @Test
    @DisplayName("a user is identified by oid, the same in every client application, not by the pairwise sub")
    void userIsIdentifiedByOid() {
        authenticate(jwt().subject("pairwise-sub-for-the-gateway").claim("oid", "3f2a-oid").claim("scp", "access_as_user")
                .claim("azp", "gateway-client").claim("email", "vp@example.com").build(), "ROLE_SALES_VP");

        assertThat(SecurityUtils.getCurrentCaller()).get().isInstanceOf(Caller.User.class)
                .extracting(Caller::actorId).isEqualTo("3f2a-oid");
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo("3f2a-oid");
        assertThat(SecurityUtils.getCurrentUserEmail()).contains("vp@example.com");
        assertThat(SecurityUtils.hasRole("ROLE_SALES_VP")).isTrue();
    }

    @Test
    @DisplayName("a token without oid falls back to sub")
    void jwtWithoutOidFallsBackToSubject() {
        authenticate(jwt().subject("oid-123").build(), "ROLE_SalesVP");

        assertThat(SecurityUtils.isAuthenticated()).isTrue();
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo("oid-123");
    }

    @Test
    @DisplayName("an app-only token (idtyp=app) is a service, service:<azp>, and not a user")
    void appOnlyTokenIsAService() {
        authenticate(jwt().subject("sp-object-id").claim("oid", "sp-object-id").claim("azp", "quote-client-id")
                .claim("idtyp", "app").build(), "ROLE_INTERNAL_INVOKE");

        assertThat(SecurityUtils.getCurrentCaller()).get().isInstanceOf(Caller.Service.class)
                .extracting(Caller::actorId).isEqualTo("service:quote-client-id");
        assertThat(SecurityUtils.isAuthenticated()).isTrue();
        assertThat(SecurityUtils.getCurrentUser()).isEmpty();
        assertThat(SecurityUtils.hasRole("ROLE_INTERNAL_INVOKE")).isFalse();
        assertThatThrownBy(SecurityUtils::getCurrentUserId).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("without idtyp, a token naming a client but carrying no delegated scope is a service")
    void clientWithoutScopeIsAService() {
        authenticate(jwt().subject("sp").claim("azp", "pricing-client-id").build());
        assertThat(SecurityUtils.getCurrentCaller()).get().extracting(Caller::actorId)
                .isEqualTo("service:pricing-client-id");

        authenticate(jwt().subject("sp").claim("appid", "v1-client-id").build());
        assertThat(SecurityUtils.getCurrentCaller()).get().extracting(Caller::actorId)
                .isEqualTo("service:v1-client-id");
    }

    @Test
    @DisplayName("the test helpers produce the same shapes: user by oid, service by client id")
    void testCallersMatchTheModel() {
        String alice = TestCallers.signInUser("alice", "DealDesk");
        assertThat(alice).isEqualTo(TestTokens.oidOf("alice"));
        assertThat(SecurityUtils.getCurrentCaller()).get().isInstanceOf(Caller.User.class);
        assertThat(SecurityUtils.getCurrentCaller().orElseThrow().hasAuthority("ROLE_DEAL_DESK")).isTrue();

        assertThat(TestCallers.signInService("quote-service", "Internal.Invoke")).isEqualTo("service:quote-service");
        assertThat(SecurityUtils.getCurrentCaller()).get().extracting(Caller::actorId)
                .isEqualTo("service:quote-service");
    }

    @Test
    @DisplayName("a non-JWT authenticated principal is a user named by the principal")
    void namedPrincipalResolvesToName() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("deal-desk-1", null, AuthorityUtils.NO_AUTHORITIES));

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo("deal-desk-1");
    }
}
