package com.opencrm.crm.fields;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throwaway PostgreSQL 16 proof for the V21 rollback.
 *
 * <p>Flyway 11 {@code repair()} fixes checksums and drops failed entries. It does
 * not forget a successful version, so this test deletes only the V21 row from
 * {@code crm.flyway_schema_history} before migrating again. {@code clean()} is
 * not used: it would drop V1 through V20 as well.
 */
@Tag("integration")
@Tag("docker")
class FieldRegistryRollbackIntegrationTest {

    private static final List<String> REGISTRY_TABLES = List.of(
            "field_definition", "field_option", "field_value");

    private static final List<String> EARLIER_TABLES = List.of(
            "organizations", "contacts", "deals");

    private static final List<String> REGISTRY_INDEXES = List.of(
            "idx_field_value_text",
            "idx_field_value_numeric",
            "idx_field_value_date",
            "idx_field_value_bool",
            "idx_field_value_option",
            "idx_field_value_user",
            "uq_field_value_organization",
            "uq_field_value_contact",
            "uq_field_value_deal",
            "uq_field_option_active_label",
            "uq_field_definition_tenant_entity_key",
            "field_definition_pkey",
            "field_option_pkey",
            "field_value_pkey");

    @Test
    void rollbackRemovesRegistryObjectsAndRemigrationRestoresThem() throws Exception {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            Flyway flyway = flyway(database);
            assertThat(flyway.info().applied()).isEmpty();

            assertThat(flyway.migrate().migrationsExecuted).isPositive();
            flyway.validate();
            assertThat(appliedVersions(flyway)).endsWith("20", "21");
            assertScriptsComeFromTheMigrationDirectory(flyway);

            try (Connection connection = jdbc(database)) {
                assertRelations(connection, REGISTRY_TABLES, true);
                assertRelations(connection, EARLIER_TABLES, true);
                assertThat(relationExists(connection, "crm", "saved_view")).isFalse();
                assertIndexes(connection, true);
                long organizations = count(connection, "SELECT count(*) FROM crm.organizations");

                runScript(connection, "db/rollback/V21__drop_field_registry.sql");

                assertRelations(connection, REGISTRY_TABLES, false);
                assertRelations(connection, EARLIER_TABLES, true);
                assertThat(relationExists(connection, "crm", "saved_view")).isFalse();
                assertIndexes(connection, false);
                assertThat(count(connection, "SELECT count(*) FROM crm.organizations")).isEqualTo(organizations);
                assertThat(appliedVersions(flyway)).endsWith("20", "21");

                flyway.repair();
                assertThat(appliedVersions(flyway)).as("repair leaves a successful V21 in place").endsWith("20", "21");

                try (Statement statement = connection.createStatement()) {
                    assertThat(statement.executeUpdate(
                            "DELETE FROM crm.flyway_schema_history WHERE version = '21'")).isEqualTo(1);
                }
            }

            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertThat(appliedVersions(flyway)).endsWith("20", "21");
            try (Connection connection = jdbc(database)) {
                assertRelations(connection, REGISTRY_TABLES, true);
                assertRelations(connection, EARLIER_TABLES, true);
                assertIndexes(connection, true);
            }
        }
    }

    private static void assertScriptsComeFromTheMigrationDirectory(Flyway flyway) {
        assertThat(flyway.info().applied()).isNotEmpty();
        for (MigrationInfo info : flyway.info().applied()) {
            if (info.getVersion() == null) {
                continue;
            }
            assertThat(info.getScript()).doesNotContain("rollback");
            assertThat(info.getScript()).startsWith("V");
        }
    }

    private static List<String> appliedVersions(Flyway flyway) {
        List<String> versions = new ArrayList<>();
        for (MigrationInfo info : flyway.info().applied()) {
            if (info.getVersion() != null) {
                versions.add(info.getVersion().getVersion());
            }
        }
        return versions;
    }

    private static void assertRelations(Connection connection, List<String> tables, boolean present) throws SQLException {
        for (String table : tables) {
            assertThat(relationExists(connection, "crm", table)).as(table).isEqualTo(present);
        }
    }

    private static void assertIndexes(Connection connection, boolean present) throws SQLException {
        for (String index : REGISTRY_INDEXES) {
            assertThat(indexExists(connection, index)).as(index).isEqualTo(present);
        }
    }

    private static boolean relationExists(Connection connection, String schema, String table) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """)) {
            statement.setString(1, schema);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static boolean indexExists(Connection connection, String index) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM pg_indexes WHERE schemaname = 'crm' AND indexname = ?
                """)) {
            statement.setString(1, index);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private static void runScript(Connection connection, String classpath) throws Exception {
        String sql;
        try (InputStream in = FieldRegistryRollbackIntegrationTest.class.getClassLoader()
                .getResourceAsStream(classpath)) {
            assertThat(in).as(classpath).isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String body = sql.replaceAll("(?m)--.*$", "");
        for (String statement : body.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                try (Statement jdbc = connection.createStatement()) {
                    jdbc.execute(trimmed);
                }
            }
        }
    }

    private static Flyway flyway(PostgreSQLContainer<?> database) {
        return Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("crm")
                .locations("classpath:db/migration")
                .placeholders(Map.of("crm_base_currency", "USD"))
                .load();
    }

    private static Connection jdbc(PostgreSQLContainer<?> database) throws SQLException {
        return DriverManager.getConnection(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }
}
