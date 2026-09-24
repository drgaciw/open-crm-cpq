package com.opencrm.security.event;

import com.opencrm.common.event.EventActorAutoConfiguration;
import com.opencrm.common.event.EventActorResolver;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.OpenCrmActorAutoConfiguration;
import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-018 D4: the event actor comes from the security context (ADR-016)")
class SecurityContextEventActorResolverTest {

    private final SecurityContextEventActorResolver resolver =
            new SecurityContextEventActorResolver(new CurrentActor("service:quote-service", false));

    @AfterEach
    void clearContext() {
        TestCallers.clear();
    }

    @Test
    @DisplayName("a person is user:<oid>")
    void userIsTheActor() {
        TestCallers.signInUser("alice", "CPQ.User");

        assertThat(resolver.currentActor()).isEqualTo("user:" + TestTokens.oidOf("alice"));
    }

    @Test
    @DisplayName("another service calling with a service token is service:<client-id>, not a user")
    void callingServiceIsTheActor() {
        TestCallers.signInService("pricing-client-id", "Internal.Invoke");

        assertThat(resolver.currentActor()).isEqualTo("service:pricing-client-id");
    }

    @Test
    @DisplayName("any other authenticated principal is user:<name>")
    void authenticatedPrincipalIsTheActor() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "dev-user", "n/a", AuthorityUtils.createAuthorityList("ROLE_SalesRep")));

        assertThat(resolver.currentActor()).isEqualTo("user:dev-user");
    }

    @Test
    @DisplayName("an anonymous caller, or none, is attributed to this service")
    void noCallerMeansThisService() {
        assertThat(resolver.currentActor()).isEqualTo("service:quote-service");

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThat(resolver.currentActor()).isEqualTo("service:quote-service");
    }

    @Test
    @DisplayName("with libs/security present, its resolver replaces the service-only default")
    void securityResolverIsPreferred() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(EventActorAutoConfiguration.class,
                        OpenCrmActorAutoConfiguration.class, SecurityEventActorAutoConfiguration.class))
                .withPropertyValues("spring.application.name=quote-service")
                .run(context -> {
                    assertThat(context).hasSingleBean(EventActorResolver.class);
                    assertThat(context.getBean(EventActorResolver.class))
                            .isInstanceOf(SecurityContextEventActorResolver.class);
                    assertThat(context.getBean(EventActorResolver.class).currentActor())
                            .isEqualTo("service:quote-service");
                });
    }
}
