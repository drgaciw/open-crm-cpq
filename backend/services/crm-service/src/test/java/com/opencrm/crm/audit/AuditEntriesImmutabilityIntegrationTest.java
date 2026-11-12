package com.opencrm.crm.audit;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the DRG-309 storage-layer guarantee: {@code audit_entries} rejects UPDATE and DELETE regardless of what
 * issues the statement, not only through the application's repository (see
 * {@code V18__audit_entries.sql}'s {@code reject_audit_entries_mutation()} trigger).
 *
 * <p><b>Known gap:</b> this test needs Docker (Testcontainers Postgres) and was written but not executed in this
 * environment - see the PR description.
 */
@Tag("integration")
class AuditEntriesImmutabilityIntegrationTest {

    @Test
    void updateOnAuditEntriesIsRejectedByTheDatabase() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            migrate(database);
            try (Connection connection = jdbc(database); Statement statement = connection.createStatement()) {
                String id = insertOneGenesisEntry(statement);

                assertThatThrownBy(() -> statement.executeUpdate(
                        "UPDATE crm.audit_entries SET details = 'tampered' WHERE id = " + id))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("append-only");
            }
        }
    }

    @Test
    void deleteOnAuditEntriesIsRejectedByTheDatabase() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            migrate(database);
            try (Connection connection = jdbc(database); Statement statement = connection.createStatement()) {
                String id = insertOneGenesisEntry(statement);

                assertThatThrownBy(() -> statement.executeUpdate("DELETE FROM crm.audit_entries WHERE id = " + id))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("append-only");

                var remaining = statement.executeQuery("SELECT count(*) FROM crm.audit_entries WHERE id = " + id);
                remaining.next();
                assertThat(remaining.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void insertOfASecondEntryChainingOffTheFirstSucceeds() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            migrate(database);
            try (Connection connection = jdbc(database); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        INSERT INTO crm.audit_entries
                            (uuid, aggregate_type, aggregate_id, action, occurred_at, previous_hash, entry_hash)
                        VALUES
                            ('11111111-1111-1111-1111-111111111111', 'Lead', '1', 'CREATE', NOW(),
                             repeat('0', 64), repeat('a', 64))""");

                statement.executeUpdate("""
                        INSERT INTO crm.audit_entries
                            (uuid, aggregate_type, aggregate_id, action, occurred_at, previous_hash, entry_hash)
                        VALUES
                            ('22222222-2222-2222-2222-222222222222', 'Lead', '1', 'UPDATE', NOW(),
                             repeat('a', 64), repeat('b', 64))""");

                var count = statement.executeQuery("SELECT count(*) FROM crm.audit_entries");
                count.next();
                assertThat(count.getInt(1)).isEqualTo(2);
            }
        }
    }

    private static String insertOneGenesisEntry(Statement statement) throws SQLException {
        statement.executeUpdate("""
                INSERT INTO crm.audit_entries
                    (uuid, aggregate_type, aggregate_id, action, occurred_at, previous_hash, entry_hash)
                VALUES
                    ('33333333-3333-3333-3333-333333333333', 'Lead', '1', 'CREATE', NOW(),
                     repeat('0', 64), repeat('c', 64))""");
        var id = statement.executeQuery(
                "SELECT id FROM crm.audit_entries WHERE entry_hash = repeat('c', 64)");
        id.next();
        return id.getString(1);
    }

    /** A brand-new container with no prior history applies every migration in plain order, so the plain
     * {@code migrate()} here behaves the same as crm-service's own {@code FlywayMigrationStrategy} would for
     * this (fresh-schema) case; see {@code CrmFlywayMigrationIntegrationTest#freshSchemaUsesTheNormalMigrationOrder}. */
    private static void migrate(PostgreSQLContainer<?> database) {
        Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("crm")
                .locations("classpath:db/migration")
                .placeholders(Map.of("crm_base_currency", "USD"))
                .load()
                .migrate();
    }

    private static Connection jdbc(PostgreSQLContainer<?> database) throws SQLException {
        return DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }
}
