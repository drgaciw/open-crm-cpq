package com.opencrm.quote.lifecycle;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Real Flyway upgrade leaves legacy priced evidence unverified. */
@Tag("integration")
@Testcontainers
class LifecyclePricingValidationMigrationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void upgradeLeavesLegacyRowsUnverifiedAndRestrictsMarkerValues() throws Exception {
        migrations("1.14").migrate();
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var insert = connection.prepareStatement("""
                     insert into quote.lifecycle_changes
                       (id, tid, intent, initiation_trigger, status, revision, version, action_date,
                        contract_effective_date, contract_id, currency_code, output_mode, created_by)
                     values (?, 'tenant-a', 'AMEND', 'MANUAL', 'PRICED', 1, 0, date '2026-01-01',
                             date '2026-01-01', ?, 'USD', 'QUOTE', 'test')
                     """)) {
            insert.setObject(1, id);
            insert.setObject(2, UUID.randomUUID());
            assertThat(insert.executeUpdate()).isEqualTo(1);
        }
        migrations(null).migrate();
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("select pricing_validation_version from quote.lifecycle_changes where id = '" + id + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject(1)).isNull();
            }
            assertThat(statement.executeUpdate("update quote.lifecycle_changes set pricing_validation_version = 1 where id = '" + id + "'"))
                    .isEqualTo(1);
            assertThatThrownBy(() -> statement.executeUpdate("update quote.lifecycle_changes set pricing_validation_version = 2 where id = '" + id + "'"))
                    .isInstanceOf(SQLException.class);
            try (var rows = statement.executeQuery("select pricing_validation_version, status from quote.lifecycle_changes where id = '" + id + "'")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(1);
                assertThat(rows.getString(2)).isEqualTo("PRICED");
            }
        }
    }

    private static Flyway migrations(String target) {
        var config = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("quote").defaultSchema("quote").locations("classpath:db/migration");
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load();
    }
}
