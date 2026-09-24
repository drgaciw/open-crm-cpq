package com.opencrm.security;

import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.domain.AuditorAware;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-016 decision 6: the current actor and the JPA auditor")
class OpenCrmActorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenCrmActorAutoConfiguration.class))
            .withPropertyValues("spring.application.name=approval-routing");

    @AfterEach
    void clear() {
        TestCallers.clear();
    }

    @Test
    @DisplayName("this service is service:<client-id> with a workload identity, service:<name> without")
    void selfActor() {
        runner.run(context -> assertThat(context.getBean(CurrentActor.class).self())
                .isEqualTo("service:approval-routing"));
        runner.withPropertyValues("AZURE_CLIENT_ID=11111111-aaaa-4bbb-8ccc-000000000001")
                .run(context -> assertThat(context.getBean(CurrentActor.class).self())
                        .isEqualTo("service:11111111-aaaa-4bbb-8ccc-000000000001"));
        runner.withPropertyValues("AZURE_CLIENT_ID=from-webhook", "opencrm.security.client.client-id=configured")
                .run(context -> assertThat(context.getBean(CurrentActor.class).self())
                        .isEqualTo("service:configured"));
    }

    @Test
    @DisplayName("the auditor records the verified caller, else this service")
    void auditorRecordsTheActor() {
        runner.run(context -> {
            @SuppressWarnings("unchecked")
            AuditorAware<String> auditor = context.getBean(AuditorAware.class);

            assertThat(auditor.getCurrentAuditor()).contains("service:approval-routing");

            TestCallers.signInUser("alice", "CPQ.User");
            assertThat(auditor.getCurrentAuditor()).contains(TestTokens.oidOf("alice"));

            TestCallers.signInService("quote-client-id", "Internal.Invoke");
            assertThat(auditor.getCurrentAuditor()).contains("service:quote-client-id");
        });
    }

    @Test
    @DisplayName("only dev mode lets an action proceed without a verified caller")
    void unverifiedCallersOnlyInDev() {
        runner.run(context -> assertThat(context.getBean(CurrentActor.class).unverifiedCallersAllowed()).isFalse());
        runner.withPropertyValues("opencrm.security.mode=dev")
                .run(context -> assertThat(context.getBean(CurrentActor.class).unverifiedCallersAllowed()).isTrue());
        runner.withPropertyValues("opencrm.security.mode=dev", "opencrm.env=prod")
                .run(context -> assertThat(context).hasFailed());
    }
}
