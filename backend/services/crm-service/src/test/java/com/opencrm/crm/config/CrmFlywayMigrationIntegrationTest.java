package com.opencrm.crm.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class CrmFlywayMigrationIntegrationTest {
    @TempDir Path migrations;

    @Test
    void freshSchemaUsesTheNormalMigrationOrder() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(true);
            Flyway current = flyway(database);
            CrmFlywayMigrationConfiguration.migrate(current);
            current.validate();
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList()).contains("8", "9", "15");
            assertThat(current.info().pending()).isEmpty();
        }
    }

    @Test
    void recoversBothLateMigrationsWithoutChangingAppliedHistory() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(false);
            Flyway initial = flyway(database);
            initial.migrate();
            copyMigrations(true);
            Flyway current = flyway(database);
            assertThatThrownBy(current::validate).hasMessageContaining("not applied to database");
            CrmFlywayMigrationConfiguration.migrate(current);
            current.validate();
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList())
                    .contains("8", "9", "10", "11", "15");
            CrmFlywayMigrationConfiguration.migrate(current);
            assertThat(current.info().pending()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "9"})
    void recoversEitherLateMigrationIndependently(String missing) throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(true);
            try (Stream<Path> files = Files.list(migrations)) {
                for (Path file : files.filter(f -> f.getFileName().toString().startsWith("V" + missing + "__")).toList()) {
                    Files.delete(file);
                }
            }
            flyway(database).migrate();
            copyMigrations(true);
            Flyway current = flyway(database);
            CrmFlywayMigrationConfiguration.migrate(current);
            current.validate();
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList()).contains("8", "9", "15");
        }
    }

    @Test
    void neverRunsPastAnExplicitLowerTarget() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(false);
            flyway(database).migrate();
            copyMigrations(true);
            Flyway current = Flyway.configure().configuration(flyway(database).getConfiguration())
                    .target("8").load();
            CrmFlywayMigrationConfiguration.migrate(current);
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList()).contains("8").doesNotContain("9");
        }
    }

    @Test
    void validatesExistingChecksumsBeforeRecovering() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(false);
            flyway(database).migrate();
            copyMigrations(true);
            Path changed;
            try (Stream<Path> files = Files.list(migrations)) {
                changed = files.filter(f -> f.getFileName().toString().startsWith("V6__")).findFirst().orElseThrow();
            }
            Files.writeString(changed, Files.readString(changed) + "\n-- changed checksum\n");
            Flyway current = flyway(database);
            assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(current))
                    .hasMessageContaining("checksum mismatch");
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList()).doesNotContain("8", "9");
        }
    }

    @Test
    void concurrentRecoveriesOnOneSchemaAreSerializedByFlyway() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(false);
            flyway(database).migrate();
            copyMigrations(true);
            var start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> { start.await(); CrmFlywayMigrationConfiguration.migrate(flyway(database)); return null; });
                var second = executor.submit(() -> { start.await(); CrmFlywayMigrationConfiguration.migrate(flyway(database)); return null; });
                start.countDown();
                first.get(30, TimeUnit.SECONDS);
                second.get(30, TimeUnit.SECONDS);
            }
            Flyway result = flyway(database);
            result.validate();
            assertThat(Stream.of(result.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList())
                    .filteredOn(version -> version.equals("8") || version.equals("9"))
                    .containsExactlyInAnyOrder("8", "9");
        }
    }

    @Test
    void conflictingManualSchemaChangesFailWithoutRecordingRecovery() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(false);
            flyway(database).migrate();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("ALTER TABLE crm.organizations ADD COLUMN parent_organization_id BIGINT");
            }
            copyMigrations(true);
            Flyway current = flyway(database);
            assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(current))
                    .hasMessageContaining("parent_organization_id");
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList()).doesNotContain("8", "9");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "9", "15"})
    void refusesToAssumeThatABaselineContainsLateMigrationSchema(String version) throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(true);
            Flyway baseline = Flyway.configure().configuration(flyway(database).getConfiguration())
                    .baselineVersion(version).load();
            baseline.baseline();
            Flyway current = flyway(database);
            assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(current))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("baseline covers migrations 8/9");
            assertThat(current.info().current().getVersion().getVersion()).isEqualTo(version);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"8", "9", "15"})
    void refusesAutomaticBaselineThatCouldSkipTheLateMigrations(String version) throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(true);
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA crm");
                statement.execute("CREATE TABLE crm.untracked (id int)");
            }
            Flyway current = Flyway.configure().configuration(flyway(database).getConfiguration())
                    .baselineVersion(version).baselineOnMigrate(true).load();
            assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(current))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("automatic baseline");
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT to_regclass('crm.flyway_schema_history') IS NULL")) {
                result.next();
                assertThat(result.getBoolean(1)).isTrue();
            }
        }
    }

    @Test
    void retriesOnlyTheUnappliedMigrationAfterPartialRecovery() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(false);
            flyway(database).migrate();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE crm.territory_assignment_rules (id bigint)");
            }
            copyMigrations(true);
            Flyway current = flyway(database);
            assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(current))
                    .hasMessageContaining("territory_assignment_rules");
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList()).contains("8").doesNotContain("9");
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("DROP TABLE crm.territory_assignment_rules");
            }
            CrmFlywayMigrationConfiguration.migrate(current);
            current.validate();
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList())
                    .filteredOn(version -> version.equals("8") || version.equals("9"))
                    .containsExactlyInAnyOrder("8", "9");
        }
    }

    @Test
    void refusesAnyOtherIgnoredMigrationBeforeApplyingRecovery() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            copyMigrations(false);
            flyway(database).migrate();
            copyMigrations(true);
            Files.writeString(migrations.resolve("V0__unexpected.sql"), "select 1;");
            Flyway current = flyway(database);
            assertThatThrownBy(() -> CrmFlywayMigrationConfiguration.migrate(current))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("Unexpected ignored");
            assertThat(Stream.of(current.info().applied()).filter(i -> i.getVersion() != null)
                    .map(i -> i.getVersion().getVersion()).toList())
                    .doesNotContain("8", "9");
        }
    }

    private void copyMigrations(boolean includeLate) throws Exception {
        Path source = Path.of(getClass().getClassLoader().getResource("db/migration").toURI());
        try (Stream<Path> files = Files.list(source)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!includeLate && (name.startsWith("V8__") || name.startsWith("V9__"))) continue;
                Files.copy(file, migrations.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Flyway flyway(PostgreSQLContainer<?> db) {
        return Flyway.configure().dataSource(db.getJdbcUrl(), db.getUsername(), db.getPassword())
                .schemas("crm").locations("filesystem:" + migrations)
                .placeholders(Map.of("crm_base_currency", "USD"))
                .load();
    }
}
