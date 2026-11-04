package com.opencrm.crm.config;

import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationPattern;
import org.flywaydb.core.api.configuration.Configuration;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrmFlywayMigrationConfigurationTest {
    @Test
    void registersRecoveryStrategyForBootFlyway() {
        new ApplicationContextRunner().withUserConfiguration(CrmFlywayMigrationConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(FlywayMigrationStrategy.class));
    }
    @Test
    void refusesSkipExecutionAndCherryPickConfigurations() {
        Flyway flyway = mock(Flyway.class);
        Configuration config = mock(Configuration.class);
        when(flyway.getConfiguration()).thenReturn(config);
        when(config.isSkipExecutingMigrations()).thenReturn(true);
        assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(flyway))
                .hasMessageContaining("skip-execution");
        when(config.isSkipExecutingMigrations()).thenReturn(false);
        when(config.getCherryPick()).thenReturn(new MigrationPattern[1]);
        assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(flyway))
                .hasMessageContaining("cherry-pick");
    }
}
