package com.opencrm.security.event;

import com.opencrm.common.event.EventActorAutoConfiguration;
import com.opencrm.common.event.EventActorResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-018 D4: the event actor comes from the security context (ADR-016)")
class SecurityContextEventActorResolverTest {

    private final SecurityContextEventActorResolver resolver = new SecurityContextEventActorResolver("quote-service");

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a JWT user is user:<sub>")
    void jwtUserIsTheActor() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("oid-123")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_SalesRep")));

        assertThat(resolver.currentActor()).isEqualTo("user:oid-123");
    }

    @Test
    @DisplayName("any other authenticated principal is user:<name>")
    void authenticatedPrincipalIsTheActor() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "dev-user", "n/a", AuthorityUtils.createAuthorityList("ROLE_SalesRep")));

        assertThat(resolver.currentActor()).isEqualTo("user:dev-user");
    }

    @Test
    @DisplayName("an anonymous caller, or none, is attributed to the service")
    void noUserMeansTheService() {
        assertThat(resolver.currentActor()).isEqualTo("service:quote-service");

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(resolver.currentActor()).isEqualTo("service:quote-service");
    }

    @Test
    @DisplayName("with libs/security present, its resolver replaces the service-only default")
    void securityResolverIsPreferred() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        EventActorAutoConfiguration.class, SecurityEventActorAutoConfiguration.class))
                .withPropertyValues("spring.application.name=quote-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventActorResolver.class);
                    assertThat(context.getBean(EventActorResolver.class))
                            .isInstanceOf(SecurityContextEventActorResolver.class);
                });
    }
}
