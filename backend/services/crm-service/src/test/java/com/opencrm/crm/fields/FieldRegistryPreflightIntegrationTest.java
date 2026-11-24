package com.opencrm.crm.fields;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@Tag("docker")
class FieldRegistryPreflightIntegrationTest {

    private static final String CONTAINER_SCRIPT = "/tmp/field-registry-v21-preflight.sql";

    @Test
    void recognizesCleanV20ThenOfficialV21ReadyForV22() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            Flyway latest = flyway(database);
            migrateTo(latest, "20");
            JdbcTemplate jdbc = jdbc(database);
            jdbc.update("INSERT INTO crm.organizations (uuid, name) VALUES ('preflight-fixture', 'Existing account')");

            assertAllowed(probe(database), "CRM_V21_READY_TO_MIGRATE");
            assertThat(jdbc.queryForObject("SELECT to_regclass('crm.field_definition')::text", String.class)).isNull();

            migrateTo(latest, "21");
            assertAllowed(probe(database), "CRM_V22_READY_TO_MIGRATE");
            assertThat(jdbc.queryForMap("SELECT script, checksum FROM crm.flyway_schema_history WHERE version = '21'"))
                    .containsEntry("script", "V21__field_registry.sql")
                    .containsEntry("checksum", 896777634);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm.field_definition", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT name FROM crm.organizations WHERE uuid = 'preflight-fixture'", String.class))
                    .isEqualTo("Existing account");
        }
    }

    @Test
    void blocksFixtureAndManualNameCollisionsWithoutAdoptingObjects() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            migrateTo(flyway(database), "20");
            JdbcTemplate jdbc = jdbc(database);
            jdbc.execute("CREATE TABLE crm.field_definition (label text)");
            jdbc.update("INSERT INTO crm.field_definition VALUES ('Preserved fixture')");
            jdbc.execute("CREATE VIEW crm.field_option AS SELECT 1 AS id");
            jdbc.execute("CREATE INDEX idx_field_value_text ON crm.organizations (name)");

            Container.ExecResult result = probe(database);

            assertBlocked(result, "CRM_V21_BLOCKED_OBJECT_COLLISION");
            assertThat(result.getStderr()).contains("field_definition", "field_option", "idx_field_value_text");
            assertThat(jdbc.queryForObject("SELECT label FROM crm.field_definition", String.class))
                    .isEqualTo("Preserved fixture");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm.flyway_schema_history WHERE version = '21'", Long.class))
                    .isZero();
        }
    }

    @Test
    void blocksWrongChecksumAndIncompleteTablesWithoutRepairingHistory() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            migrateTo(flyway(database), "21");
            JdbcTemplate jdbc = jdbc(database);
            int checksum = jdbc.queryForObject("SELECT checksum FROM crm.flyway_schema_history WHERE version = '21'", Integer.class);
            int differentChecksum = ~checksum;
            jdbc.update("UPDATE crm.flyway_schema_history SET checksum = ? WHERE version = '21'", differentChecksum);

            assertBlocked(probe(database), "CRM_V21_BLOCKED_HISTORY_MISMATCH");
            assertThat(jdbc.queryForObject("SELECT checksum FROM crm.flyway_schema_history WHERE version = '21'", Integer.class))
                    .isEqualTo(differentChecksum);

            jdbc.update("UPDATE crm.flyway_schema_history SET checksum = ? WHERE version = '21'", checksum);
            jdbc.execute("ALTER TABLE crm.field_value RENAME TO field_value_preserved");

            Container.ExecResult result = probe(database);

            assertBlocked(result, "CRM_V21_BLOCKED_TABLES_INCOMPLETE");
            assertThat(result.getStderr()).contains("field_value");
            assertThat(jdbc.queryForObject("SELECT to_regclass('crm.field_value_preserved')::text", String.class))
                    .isEqualTo("crm.field_value_preserved");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm.flyway_schema_history WHERE version = '21'", Long.class))
                    .isEqualTo(1);
        }
    }

    @Test
    void blocksRetiredCandidateHistoryWithoutAdoptingIt() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            migrateTo(flyway(database), "21");
            JdbcTemplate jdbc = jdbc(database);
            jdbc.update("""
                    UPDATE crm.flyway_schema_history
                    SET checksum = -696508314, script = 'V21__create_field_registry.sql', description = 'create field registry'
                    WHERE version = '21'
                    """);

            assertBlocked(probe(database), "CRM_V21_BLOCKED_RETIRED_CANDIDATE");
            assertThat(jdbc.queryForMap("SELECT script, checksum FROM crm.flyway_schema_history WHERE version = '21'"))
                    .containsEntry("script", "V21__create_field_registry.sql")
                    .containsEntry("checksum", -696508314);
            assertThat(jdbc.queryForObject("SELECT to_regclass('crm.field_value')::text", String.class))
                    .isEqualTo("crm.field_value");
        }
    }

    @Test
    void countsIncompatibleV21RowsWithoutRevealingOrChangingThem() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            migrateTo(flyway(database), "21");
            JdbcTemplate jdbc = jdbc(database);
            long organization = jdbc.queryForObject("""
                    INSERT INTO crm.organizations (uuid, name)
                    VALUES ('preflight-org', 'Preserved organization') RETURNING id
                    """, Long.class);
            long contact = jdbc.queryForObject("""
                    INSERT INTO crm.contacts (uuid, first_name, last_name)
                    VALUES ('preflight-contact', 'Preserved', 'contact') RETURNING id
                    """, Long.class);
            long wrongEntity = definition(jdbc, "sealed-tenant-fixture", "entity", "text");
            long wrongStorage = definition(jdbc, "sealed-tenant-fixture", "storage", "number");
            long select = definition(jdbc, "sealed-tenant-fixture", "choice", "select");
            long other = definition(jdbc, "sealed-tenant-fixture", "other", "select");
            definition(jdbc, "   ", "blank_tenant", "text");
            long foreignOption = jdbc.queryForObject("""
                    INSERT INTO crm.field_option (definition_id, label, position)
                    VALUES (?, 'Preserved option', 1) RETURNING id
                    """, Long.class, other);
            jdbc.update("INSERT INTO crm.field_value (definition_id, contact_id, \"text\") VALUES (?, ?, ?)",
                    wrongEntity, contact, "sealed-value-fixture");
            jdbc.update("INSERT INTO crm.field_value (definition_id, organization_id, \"text\") VALUES (?, ?, ?)",
                    wrongStorage, organization, "sealed-value-fixture");
            jdbc.update("INSERT INTO crm.field_value (definition_id, organization_id, option_id) VALUES (?, ?, ?)",
                    select, organization, foreignOption);

            Container.ExecResult result = probe(database);

            assertBlocked(result, "CRM_V22_BLOCKED_INVALID_REGISTRY_DATA");
            assertThat(result.getStderr())
                    .contains("blank_tenants=1", "entity_mismatches=1", "storage_mismatches=1", "foreign_options=1")
                    .doesNotContain("sealed-tenant-fixture", "sealed-value-fixture", "Preserved option",
                            "Preserved organization");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm.field_definition", Long.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm.field_value", Long.class)).isEqualTo(3);
            assertThat(jdbc.queryForObject("""
                    SELECT option_id FROM crm.field_value WHERE definition_id = ?
                    """, Long.class, select)).isEqualTo(foreignOption);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm.flyway_schema_history WHERE version = '22'", Long.class))
                    .isZero();
        }
    }

    @Test
    void recognizesCanonicalV22AndRejectsItsWrongChecksum() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            Flyway latest = flyway(database);
            migrateTo(latest, "21");
            JdbcTemplate jdbc = jdbc(database);
            long organization = jdbc.queryForObject(
                    "INSERT INTO crm.organizations (uuid, name) VALUES ('preflight-v22', 'Preserved account') RETURNING id",
                    Long.class);
            long definition = definition(jdbc, "fixture-tenant", "kept_amount", "number");
            jdbc.update("INSERT INTO crm.field_value (definition_id, organization_id, \"numeric\") VALUES (?, ?, 12.50)",
                    definition, organization);

            assertAllowed(probe(database), "CRM_V22_READY_TO_MIGRATE");
            assertThat(latest.migrate().migrationsExecuted).isEqualTo(1);
            latest.validate();
            assertAllowed(probe(database), "CRM_V22_ALREADY_APPLIED");
            assertThat(jdbc.queryForMap("SELECT script, checksum FROM crm.flyway_schema_history WHERE version = '22'"))
                    .containsEntry("script", "V22__strengthen_field_registry_integrity.sql")
                    .containsEntry("checksum", -1374655337);
            assertThat(jdbc.queryForMap(
                    "SELECT \"numeric\", record_entity, storage_class FROM crm.field_value WHERE definition_id = ?", definition))
                    .containsEntry("numeric", new BigDecimal("12.5000"))
                    .containsEntry("record_entity", "organization")
                    .containsEntry("storage_class", "numeric");

            jdbc.update("UPDATE crm.flyway_schema_history SET checksum = 0 WHERE version = '22'");
            assertBlocked(probe(database), "CRM_V21_BLOCKED_HISTORY_MISMATCH");
            assertThat(jdbc.queryForObject("SELECT checksum FROM crm.flyway_schema_history WHERE version = '22'", Integer.class))
                    .isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM crm.field_value", Long.class)).isEqualTo(1);
        }
    }

    @Test
    void refusesMissingV20History() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();

            assertBlocked(probe(database), "CRM_V21_BLOCKED_NO_V20_HISTORY");
            assertThat(jdbc(database).queryForObject("SELECT to_regnamespace('crm')::text", String.class)).isNull();
        }
    }

    private static Flyway flyway(PostgreSQLContainer<?> database) {
        return Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                .schemas("crm").locations("classpath:db/migration")
                .placeholders(Map.of("crm_base_currency", "USD")).load();
    }

    private static void migrateTo(Flyway latest, String version) {
        Flyway.configure().configuration(latest.getConfiguration()).target(version).load().migrate();
    }

    private static long definition(JdbcTemplate jdbc, String tenant, String key, String type) {
        return jdbc.queryForObject("""
                INSERT INTO crm.field_definition
                    (tenant, entity, "key", label, type, required, show_on_record, show_on_list, position, agent_filled)
                VALUES (?, 'organization', ?, 'Preserved field', ?, false, true, false, 0, false)
                RETURNING id
                """, Long.class, tenant, key, type);
    }

    private static JdbcTemplate jdbc(PostgreSQLContainer<?> database) {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(database.getJdbcUrl());
        dataSource.setUser(database.getUsername());
        dataSource.setPassword(database.getPassword());
        return new JdbcTemplate(dataSource);
    }

    private static Container.ExecResult probe(PostgreSQLContainer<?> database) throws Exception {
        database.copyFileToContainer(MountableFile.forHostPath(Path.of("src/main/ops/field-registry-v21-preflight.sql")),
                CONTAINER_SCRIPT);
        return database.execInContainer("psql", "-X", "--no-password", "--username", database.getUsername(),
                "--dbname", database.getDatabaseName(), "--file", CONTAINER_SCRIPT);
    }

    private static void assertAllowed(Container.ExecResult result, String state) {
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        assertThat(result.getStderr()).contains(state);
    }

    private static void assertBlocked(Container.ExecResult result, String state) {
        assertThat(result.getExitCode()).as(result.getStderr()).isEqualTo(3);
        assertThat(result.getStderr()).contains(state);
    }
}
