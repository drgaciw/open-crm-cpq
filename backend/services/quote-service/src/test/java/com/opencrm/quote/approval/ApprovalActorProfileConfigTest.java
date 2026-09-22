package com.opencrm.quote.approval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The unverified-actor fallback must be on in {@code dev} only. Loads the real
 * {@code application.yml} through {@link SpringApplicationBuilder} with an empty configuration
 * class, so no auto-configuration, datasource or Docker is involved.
 */
@DisplayName("approvals.allow-unverified-actor is dev-only (ADR-016 phase 0)")
class ApprovalActorProfileConfigTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    private static Boolean allowUnverifiedActor(String profile) {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                // A command-line arg outranks SPRING_PROFILES_ACTIVE, so the ambient env cannot leak in.
                .run("--spring.profiles.active=" + profile)) {
            return ctx.getEnvironment().getProperty("approvals.allow-unverified-actor", Boolean.class);
        }
    }

    @Test
    @DisplayName("dev profile turns the fallback on")
    void devAllows() {
        assertThat(allowUnverifiedActor("dev")).isTrue();
    }

    @Test
    @DisplayName("a non-dev profile leaves it off")
    void nonDevRefuses() {
        assertThat(allowUnverifiedActor("docker")).isFalse();
    }
}
