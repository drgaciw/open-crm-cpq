package com.opencrm.quote.quote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DRG-546. V1_12 backfills continuation_operation from continuation_request_json and keeps
 * EXTENSION distinguishable from RENEWAL while deal_type stays RENEWAL.
 */
@Tag("integration")
@Testcontainers
class ContinuationOperationMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig()
                    .withMemory(256L * 1024 * 1024).withMemorySwap(256L * 1024 * 1024));

    @Test
    void backfillDistinguishesExtensionFromRenewalAndRollbackDropsOnlyTheColumn() throws Exception {
        migrations("1.11").migrate();

        long extensionId = insert("RENEWAL", " { \"operation\" : \"EXTENSION\" } ");
        long renewalId = insert("RENEWAL", "{\"operation\":\"RENEWAL\",\"quantity\":50}");
        long amendmentId = insert("AMENDMENT", "{\"operation\":\"AMENDMENT\"}");
        long blankId = insert("NEW", null);
        long garbageId = insert("RENEWAL", "{not-json");
        long unknownId = insert("EXPANSION", "{\"operation\":\"RENEW\"}");

        migrations(null).migrate();

        assertThat(operation(extensionId)).isEqualTo("EXTENSION");
        assertThat(operation(renewalId)).isEqualTo("RENEWAL");
        assertThat(operation(amendmentId)).isEqualTo("AMENDMENT");
        assertThat(operation(blankId)).isNull();
        assertThat(operation(garbageId)).isNull();
        assertThat(operation(unknownId)).isNull();
        assertThat(dealType(extensionId)).isEqualTo("RENEWAL");
        assertThat(dealType(renewalId)).isEqualTo("RENEWAL");
        assertThat(dealType(amendmentId)).isEqualTo("AMENDMENT");
        assertThat(json(extensionId)).contains("EXTENSION");
        assertThat(indexExists()).isTrue();

        long stored = insert("RENEWAL", "{\"operation\":\"EXTENSION\"}", "EXTENSION");
        assertThat(operation(stored)).isEqualTo("EXTENSION");
        assertThat(dealType(stored)).isEqualTo("RENEWAL");
        assertThatThrownBy(() -> insert("RENEWAL", null, "BOGUS"))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23514");
        assertThatThrownBy(() -> insert("EXTENSION", null, null))
                .isInstanceOf(SQLException.class)
                .extracting(error -> ((SQLException) error).getSQLState())
                .isEqualTo("23514");

        rollback();
        assertThat(columnExists()).isFalse();
        assertThat(indexExists()).isFalse();
        assertThat(json(extensionId)).contains("EXTENSION");
        assertThat(json(renewalId)).contains("RENEWAL");
        assertThat(dealType(extensionId)).isEqualTo("RENEWAL");
    }

    private static Flyway migrations(String target) {
        var configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("quote")
                .defaultSchema("quote")
                .locations("classpath:db/migration")
                .cleanDisabled(false);
        if (target != null) configuration.target(MigrationVersion.fromVersion(target));
        return configuration.load();
    }

    private static long insert(String dealType, String continuationJson) throws SQLException {
        return insert(dealType, continuationJson, null);
    }

    private static long insert(String dealType, String continuationJson, String operation) throws SQLException {
        String sql = operation == null
                ? "INSERT INTO quote.quotes(uuid, quote_number, name, customer_external_id, deal_type, continuation_request_json) VALUES (?,?,?,?,?,?) RETURNING id"
                : "INSERT INTO quote.quotes(uuid, quote_number, name, customer_external_id, deal_type, continuation_request_json, continuation_operation) VALUES (?,?,?,?,?,?,?) RETURNING id";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            String id = UUID.randomUUID().toString();
            statement.setString(1, id);
            statement.setString(2, "Q-" + id);
            statement.setString(3, "Synthetic continuation");
            statement.setString(4, "synthetic-customer");
            statement.setString(5, dealType);
            statement.setString(6, continuationJson);
            if (operation != null) statement.setString(7, operation);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static String operation(long id) throws SQLException {
        return scalar(id, "continuation_operation");
    }

    private static String dealType(long id) throws SQLException {
        return scalar(id, "deal_type");
    }

    private static String json(long id) throws SQLException {
        return scalar(id, "continuation_request_json");
    }

    private static String scalar(long id, String column) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + column + " FROM quote.quotes WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static boolean columnExists() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM information_schema.columns WHERE table_schema = 'quote' AND table_name = 'quotes' AND column_name = 'continuation_operation'")) {
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static boolean indexExists() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM pg_indexes WHERE schemaname = 'quote' AND indexname = 'idx_quotes_continuation_operation'")) {
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** Same statements as the rollback block in V1_12. */
    private static void rollback() throws SQLException {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute("DROP INDEX IF EXISTS quote.idx_quotes_continuation_operation");
            statement.execute("ALTER TABLE quote.quotes DROP CONSTRAINT IF EXISTS chk_quotes_continuation_operation");
            statement.execute("ALTER TABLE quote.quotes DROP COLUMN IF EXISTS continuation_operation");
        }
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
