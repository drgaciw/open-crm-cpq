package com.opencrm.pricing.ramp.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class MigrationWindowPropertiesTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void defaultTwelveAndConfiguredSixMonthMaximumBindAtThePublishedPrefix() {
        context.run(application -> assertThat(application.getBean(MigrationWindowProperties.class)
                .getMigrationWindowMonths()).isEqualTo(12));
        context.withPropertyValues("opencrm.pricing.ramp.migration-window-months=6")
                .run(application -> assertThat(application.getBean(MigrationWindowProperties.class)
                        .getMigrationWindowMonths()).isEqualTo(6));
    }

    @Test
    void invalidZeroMaximumFailsConfigurationBinding() {
        context.withPropertyValues("opencrm.pricing.ramp.migration-window-months=0")
                .run(application -> assertThat(application).hasFailed());
    }

    @Test
    void configuredMaximumCannotExceedTheAcceptedContractCeiling() {
        context.withPropertyValues("opencrm.pricing.ramp.migration-window-months=13")
                .run(application -> assertThat(application).hasFailed());
    }

    @Test
    void policyConstructionCannotBypassTheAcceptedContractCeiling() {
        assertThat(new HybridMigrationPolicy(12)).isNotNull();
        assertThatThrownBy(() -> new HybridMigrationPolicy(13)).isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MigrationWindowProperties.class)
    static class PropertiesConfiguration {}
}
