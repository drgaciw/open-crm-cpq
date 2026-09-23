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

    // Migration counts per service. Each ADR-018 step that touches outbox_events adds one per schema:
    // D2/D3 relay status, D6 inbox (approval, contract), D4 envelope columns and the SKIPPED status.
    private static final List<Service> SERVICES = List.of(
            new Service("configuration-engine", "configuration", "configuration_sessions", 4),
            // V1_1 tables, V1_2 governance types, V1_3 JEV decision log (ADR-015), V1_4 relay, V1_5 inbox, V1_6 envelope, V1_7 SKIPPED
            new Service("approval-routing", "approval", "approval_requests", 7),
            new Service("document-generation", "document", "document_generation_jobs", 4),
            new Service("contract-asset-management", "contract", "subscriptions", 5));

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
