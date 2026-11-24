package com.opencrm.contract.asset;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Throwaway PostgreSQL 16 proof for the CAM V1_14 and V1_13 rollbacks.
 *
 * <p>The empty database is migrated to the historical V1_22 CAM baseline. V1_14's rollback
 * runs before V1_13's. Flyway 11 {@code repair()} does not forget a successful
 * version, and {@code clean()} would drop the rest of the schema, so this test
 * deletes only versions 1.13 and 1.14 from {@code contract.flyway_schema_history}
 * and migrates again with {@code outOfOrder} so those two versions re-apply
 * under the already-applied later migrations.
 */
@Tag("integration")
class AssetRollbackIntegrationTest {

    private static final String SCHEMA = "contract";

    private static final List<String> ROLLED_BACK = List.of("asset_history", "assets");

    private static final List<String> NEIGHBORS = List.of(
            "subscriptions", "document_apply_ledger", "amendment_application_units", "signature_envelopes");

    private static final List<String> INDEXES = List.of(
            "idx_asset_history_successor",
            "idx_asset_history_predecessor",
            "idx_asset_history_type",
            "idx_asset_history_account",
            "idx_asset_history_asset",
            "asset_history_pkey",
            "uq_asset_history_event",
            "idx_asset_subscription",
            "idx_asset_contract_line",
            "idx_asset_account",
            "assets_pkey",
            "assets_uuid_key",
            "uq_asset_subscription_contract_line");

    @Test
    void rollbackRemovesAssetObjectsAndRemigrationRestoresThem() throws Exception {
        try (PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            Flyway flyway = flyway(database, false);
            assertThat(flyway.info().applied()).isEmpty();

            assertThat(flyway.migrate().migrationsExecuted).isPositive();
            flyway.validate();
            List<String> versions = appliedVersions(flyway);
            assertThat(versions).contains("1.13", "1.14").endsWith("1.23");
            assertVersionedScriptsStayInTheMigrationDirectory(flyway);

            try (Connection connection = jdbc(database)) {
                assertRelations(connection, ROLLED_BACK, true);
                assertRelations(connection, NEIGHBORS, true);
                assertIndexes(connection, true);
                long subscriptions = count(connection, "SELECT count(*) FROM contract.subscriptions");

                try (Statement statement = connection.createStatement()) {
                    statement.execute("SET search_path TO contract");
                }
                runScript(connection, "db/rollback/V1_14__drop_asset_history.sql");
                runScript(connection, "db/rollback/V1_13__drop_assets.sql");

                assertRelations(connection, ROLLED_BACK, false);
                assertRelations(connection, NEIGHBORS, true);
                assertIndexes(connection, false);
                assertThat(count(connection, "SELECT count(*) FROM contract.subscriptions")).isEqualTo(subscriptions);
                assertThat(appliedVersions(flyway)).contains("1.13", "1.14");

                flyway.repair();
                assertThat(appliedVersions(flyway))
                        .as("repair leaves successful 1.13 and 1.14 rows in place")
                        .contains("1.13", "1.14");

                try (Statement statement = connection.createStatement()) {
                    assertThat(statement.executeUpdate("""
                            DELETE FROM contract.flyway_schema_history
                            WHERE version IN ('1.13', '1.14')
                            """)).isEqualTo(2);
                }
            }

            Flyway replay = flyway(database, true);
            assertThat(replay.migrate().migrationsExecuted).isEqualTo(2);
            assertThat(replay.migrate().migrationsExecuted).as("schema is current after the out-of-order re-apply").isZero();
            replay.validate();
            List<String> replayed = appliedVersions(replay);
            assertThat(replayed).contains("1.15", "1.22", "1.23");
            assertThat(replayed).endsWith("1.13", "1.14");
            try (Connection connection = jdbc(database)) {
                assertRelations(connection, ROLLED_BACK, true);
                assertRelations(connection, NEIGHBORS, true);
                assertIndexes(connection, true);
            }
        }
    }

    private static void assertVersionedScriptsStayInTheMigrationDirectory(Flyway flyway) {
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
            assertThat(relationExists(connection, table)).as(table).isEqualTo(present);
        }
    }

    private static void assertIndexes(Connection connection, boolean present) throws SQLException {
        for (String index : INDEXES) {
            assertThat(indexExists(connection, index)).as(index).isEqualTo(present);
        }
    }

    private static boolean relationExists(Connection connection, String table) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """)) {
            statement.setString(1, SCHEMA);
            statement.setString(2, table);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static boolean indexExists(Connection connection, String index) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?
                """)) {
            statement.setString(1, SCHEMA);
            statement.setString(2, index);
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
        try (InputStream in = AssetRollbackIntegrationTest.class.getClassLoader().getResourceAsStream(classpath)) {
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

    private static Flyway flyway(PostgreSQLContainer<?> database, boolean outOfOrder) {
        return Flyway.configure()
                .dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .target("1.22")
                .outOfOrder(outOfOrder)
                .load();
    }

    private static Connection jdbc(PostgreSQLContainer<?> database) throws SQLException {
        return DriverManager.getConnection(
                database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }
}
