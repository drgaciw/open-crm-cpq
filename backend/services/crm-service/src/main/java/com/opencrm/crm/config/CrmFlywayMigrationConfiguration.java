package com.opencrm.crm.config;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Recovers the two CRM migrations merged after higher-numbered migrations. */
@Configuration(proxyBeanMethods = false)
public class CrmFlywayMigrationConfiguration {
    private static final Set<MigrationVersion> LATE_MIGRATIONS = Set.of(
            MigrationVersion.fromVersion("8"), MigrationVersion.fromVersion("9"));

    @Bean
    FlywayMigrationStrategy crmFlywayMigrationStrategy() {
        return CrmFlywayMigrationConfiguration::migrate;
    }

    static void migrate(Flyway flyway) {
        if (flyway.getConfiguration().isSkipExecutingMigrations()
                || (flyway.getConfiguration().getCherryPick() != null
                && flyway.getConfiguration().getCherryPick().length > 0)) {
            throw new IllegalStateException("CRM Flyway cherry-pick and skip-execution modes are not supported");
        }
        if (flyway.getConfiguration().isOutOfOrder()) {
            throw new IllegalStateException("CRM Flyway outOfOrder must remain disabled");
        }
        if (flyway.getConfiguration().isBaselineOnMigrate()
                && flyway.getConfiguration().getBaselineVersion().compareTo(MigrationVersion.fromVersion("8")) >= 0) {
            throw new IllegalStateException("CRM automatic baseline at version 8 or higher is not permitted");
        }
        var migrationInfo = flyway.info().all();
        if (Arrays.stream(migrationInfo).anyMatch(info -> info.getState() == MigrationState.BASELINE
                && info.getVersion().compareTo(MigrationVersion.fromVersion("8")) >= 0)) {
            throw new IllegalStateException("CRM baseline covers migrations 8/9; verify and reconcile schema before startup");
        }
        var ignored = Arrays.stream(migrationInfo)
                .filter(info -> info.getState() == MigrationState.IGNORED)
                .toList();
        if (ignored.stream().map(MigrationInfo::getVersion).anyMatch(v -> !LATE_MIGRATIONS.contains(v))) {
            throw new IllegalStateException("Unexpected ignored CRM migration; refusing out-of-order recovery: "
                    + ignored.stream().map(MigrationInfo::getVersion).toList());
        }
        if (!ignored.isEmpty()) {
            // Limit the recovery to the latest ignored migration; respect an operator's
            // lower target and retain ordinary validation for all later migrations.
            MigrationVersion recoveryTarget = ignored.stream().map(MigrationInfo::getVersion)
                    .max(Comparator.naturalOrder()).orElseThrow();
            Flyway.configure().configuration(flyway.getConfiguration())
                    .outOfOrder(true).validateOnMigrate(true).target(recoveryTarget).load().migrate();
        }
        flyway.migrate();
    }
}
