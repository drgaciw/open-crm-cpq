package com.opencrm.security;

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

@DisplayName("SecurityUtils principal resolution (ADR-016 phase 0)")
class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("an anonymous caller is not a user")
    void anonymousIsNotAUser() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(SecurityUtils.isAuthenticated()).isFalse();
        assertThat(SecurityUtils.getCurrentUser()).isEmpty();
        assertThatThrownBy(SecurityUtils::getCurrentUserId).isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("no authentication at all is not a user")
    void emptyContextIsNotAUser() {
        assertThat(SecurityUtils.isAuthenticated()).isFalse();
        assertThat(SecurityUtils.getCurrentUser()).isEmpty();
    }

    @Test
    @DisplayName("a JWT principal resolves to its subject")
    void jwtPrincipalResolvesToSubject() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("oid-123")
                .claim("email", "vp@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_SalesVP")));

        assertThat(SecurityUtils.isAuthenticated()).isTrue();
        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo("oid-123");
        assertThat(SecurityUtils.getCurrentUserEmail()).contains("vp@example.com");
        assertThat(SecurityUtils.hasRole("ROLE_SalesVP")).isTrue();
    }

    @Test
    @DisplayName("a non-JWT authenticated principal resolves to its name")
    void namedPrincipalResolvesToName() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("deal-desk-1", null, AuthorityUtils.NO_AUTHORITIES));

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo("deal-desk-1");
    }
}
