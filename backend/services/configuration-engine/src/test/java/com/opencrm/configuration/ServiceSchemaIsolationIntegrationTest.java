package com.opencrm.configuration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the four real service configurations and immutable migrations in one isolated database. */
@Tag("integration")
@Testcontainers
class ServiceSchemaIsolationIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("opencrm");

    /** Flyway versions applied to schema contract. Count is this list, not a floor. */
    private static final List<String> CONTRACT_MIGRATION_VERSIONS = List.of(
            "1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.7.1", "1.7.2", "1.8", "1.10", "1.11", "1.12",
            "1.13", "1.14", "1.15");

    /** Every base table in schema contract, including assets and the asset history. */
    private static final List<String> CONTRACT_TABLES = List.of(
            "amendment_application_units",
            "amendment_commercial_envelopes",
            "amendment_document_events",
            "amendment_document_lines",
            "amendment_document_revisions",
            "amendment_documents",
            "amendment_operation_receipts",
            "applied_quote_acceptances",
            "asset_history",
            "assets",
            "contracts",
            "document_apply_ledger",
            "flyway_schema_history",
            "outbox_events",
            "processed_events",
            "renewal_eligibility_publications",
            "renewal_policy_record",
            "subscription_rate_periods",
            "subscriptions");

    // Migration counts per service. Each ADR-018 step that touches outbox_events adds one per schema:
    // D2/D3 relay status, D6 inbox (approval, contract), D4 envelope columns and the SKIPPED status.
    private static final List<Service> SERVICES = List.of(
            new Service("configuration-engine", "configuration", "configuration_sessions", 4),
            // V1_1 tables, V1_2 governance types, V1_3 JEV decision log (ADR-015), V1_4 relay, V1_5 inbox, V1_6 envelope, V1_7 SKIPPED,
            // V1_8 superseded status, V1_9 ramp approval binding
            new Service("approval-routing", "approval", "approval_requests", 9),
            new Service("document-generation", "document", "document_generation_jobs", 4),
            // V1_1 to V1_5 as above, V1_6 rate schedule, V1_7 contract documents, V1_7_1 renewal receipts,
            // V1_7_2 frozen line membership, V1_8 header backfill functions,
            // V1_10 unpriced revision storage (S3). That file is one Flyway version and creates two tables.
            // V1_11 renewal policy records.
            // V1_9 is permanently unused; V1_12 creates the ledger, V1_13 assets,
            // V1_14 asset history, and V1_15 adds unit replay evidence.
            new Service("contract-asset-management", "contract", "subscriptions", CONTRACT_MIGRATION_VERSIONS.size()));

    @Test
    void defaultSchemasKeepTablesOutboxesAndHistoriesSeparate() throws Exception {
        for (Service service : SERVICES) {
            StandardEnvironment config = configuration(service, Map.of());
            assertThat(config.getProperty("spring.datasource.url"))
                    .contains("/opencrm?currentSchema=" + service.schema());
            assertThat(config.getProperty("spring.jpa.properties.hibernate.default_schema"))
                    .isEqualTo(service.schema());
            Flyway flyway = flyway(service, config);
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(service.migrations());
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (Connection connection = connection(config); var statement = connection.createStatement()) {
                try (var result = statement.executeQuery("SELECT count(*) FROM " + service.table())) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isZero();
                }
                statement.executeUpdate("INSERT INTO outbox_events "
                        + "(uuid, aggregate_type, aggregate_id, event_type, payload) "
                        + "VALUES ('schema-probe', 'probe', '1', 'probe', '{}')");
                try (var result = statement.executeQuery("SELECT count(*) FROM outbox_events")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(1);
                }
                try (var result = statement.executeQuery(
                        "SELECT count(*) FROM flyway_schema_history WHERE success AND type = 'SQL'")) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getInt(1)).isEqualTo(service.migrations());
                }
                if ("contract".equals(service.schema())) {
                    assertContractInventory(statement);
                }
            }
        }
        try (Connection connection = POSTGRES.createConnection(""); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT count(*) FROM information_schema.tables "
                     + "WHERE table_schema = 'public' AND table_name IN ('outbox_events', 'flyway_schema_history')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
        }
    }

    @Test
    void legacyDatabaseAndPublicSchemaOverridesPreserveExistingDataAndHistory() throws Exception {
        for (Service service : SERVICES) {
            String database = "cpq_" + service.schema();
            createDatabase(database);
            StandardEnvironment config = configuration(service, Map.of("DB_NAME", database, "DB_SCHEMA", "public"));
            assertThat(config.getProperty("spring.jpa.properties.hibernate.default_schema")).isEqualTo("public");
            Flyway flyway = flyway(service, config);
            flyway.migrate();
            try (Connection connection = connection(config); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO outbox_events "
                        + "(uuid, aggregate_type, aggregate_id, event_type, payload) "
                        + "VALUES ('existing-data', 'legacy', '1', 'legacy', '{}')");
            }
            Flyway restarted = flyway(service, configuration(service,
                    Map.of("DB_NAME", database, "DB_SCHEMA", "public")));
            restarted.validate();
            assertThat(restarted.migrate().migrationsExecuted).isZero();
            try (Connection connection = connection(config); var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT aggregate_type FROM outbox_events WHERE uuid = 'existing-data'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("legacy");
            }
        }
    }

    @Test
    void nonEmptySchemaWithoutHistoryFailsInsteadOfSilentlyBaselining() throws Exception {
        for (Service service : SERVICES) {
            String schema = service.schema() + "_unmanaged";
            StandardEnvironment config = configuration(service, Map.of("DB_SCHEMA", schema));
            try (Connection connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                statement.execute("CREATE TABLE " + schema + ".existing_data (id INTEGER)");
            }
            assertThatThrownBy(() -> flyway(service, config).migrate())
                    .isInstanceOf(FlywayException.class).hasMessageContaining("non-empty");
        }
    }

    private static void assertContractInventory(Statement statement) throws Exception {
        List<String> versions = new ArrayList<>();
        try (var result = statement.executeQuery(
                "SELECT version FROM flyway_schema_history WHERE success AND type = 'SQL' ORDER BY installed_rank")) {
            while (result.next()) {
                versions.add(result.getString(1));
            }
        }
        assertThat(versions).containsExactlyElementsOf(CONTRACT_MIGRATION_VERSIONS);
        assertThat(versions).doesNotContain("1.9");
        List<String> tables = new ArrayList<>();
        try (var result = statement.executeQuery(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'contract' AND table_type = 'BASE TABLE' ORDER BY table_name")) {
            while (result.next()) {
                tables.add(result.getString(1));
            }
        }
        assertThat(tables).containsExactlyElementsOf(CONTRACT_TABLES);

        List<String> replayColumns = new ArrayList<>();
        try (var result = statement.executeQuery(
                "SELECT column_name || ':' || data_type || ':' || is_nullable FROM information_schema.columns "
                        + "WHERE table_schema = 'contract' AND table_name = 'amendment_application_units' "
                        + "AND column_name IN ('attempt_pin_fingerprint', 'attempt_revision_no', 'block_reason') "
                        + "ORDER BY column_name")) {
            while (result.next()) replayColumns.add(result.getString(1));
        }
        assertThat(replayColumns).containsExactly(
                "attempt_pin_fingerprint:character varying:YES", "attempt_revision_no:integer:YES",
                "block_reason:character varying:YES");

        List<String> replayConstraints = new ArrayList<>();
        try (var result = statement.executeQuery(
                "SELECT conname FROM pg_constraint WHERE conrelid = 'contract.amendment_application_units'::regclass "
                        + "AND conname IN ('chk_apply_attempt_pin', 'chk_apply_attempt_revision') ORDER BY conname")) {
            while (result.next()) replayConstraints.add(result.getString(1));
        }
        assertThat(replayConstraints).containsExactly("chk_apply_attempt_pin", "chk_apply_attempt_revision");
    }

    private static void createDatabase(String name) throws Exception {
        try (Connection connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + name);
        }
    }

    private static Connection connection(StandardEnvironment config) throws Exception {
        return DriverManager.getConnection(config.getRequiredProperty("spring.datasource.url"),
                POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Flyway flyway(Service service, StandardEnvironment config) {
        return Flyway.configure()
                .dataSource(config.getRequiredProperty("spring.datasource.url"), POSTGRES.getUsername(), POSTGRES.getPassword())
                .defaultSchema(config.getRequiredProperty("spring.flyway.default-schema"))
                .schemas(config.getRequiredProperty("spring.flyway.schemas"))
                .baselineOnMigrate(config.getRequiredProperty("spring.flyway.baseline-on-migrate", Boolean.class))
                .locations("filesystem:" + resources(service).resolve("db/migration").toAbsolutePath())
                .load();
    }

    private static StandardEnvironment configuration(Service service, Map<String, Object> overrides) {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new FileSystemResource(resources(service).resolve("application.yml")));
        var environment = new StandardEnvironment();
        // Test only explicit overrides; developer shell environment must not redirect test connections.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new PropertiesPropertySource("application", yaml.getObject()));
        environment.getPropertySources().addFirst(new MapPropertySource("container", Map.of(
                "DB_HOST", POSTGRES.getHost(), "DB_PORT", POSTGRES.getMappedPort(5432))));
        environment.getPropertySources().addFirst(new MapPropertySource("overrides", overrides));
        return environment;
    }

    private static Path resources(Service service) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path services = directory.resolve("services");
            if (Files.isDirectory(services.resolve("configuration-engine"))) {
                return services.resolve(service.module()).resolve("src/main/resources");
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Run from the backend Maven reactor or one of its service modules");
    }

    private record Service(String module, String schema, String table, int migrations) { }
}
