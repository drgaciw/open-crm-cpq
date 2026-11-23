package com.opencrm.crm.fields;

import jakarta.persistence.Entity;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.hibernate.SpringImplicitNamingStrategy;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Applies every crm migration, including {@code V21}, on an empty PostgreSQL 16 database.
 * Hibernate {@code ddl-auto=validate} then checks the entity model crm-service actually
 * scans. {@code field_definition}, {@code field_option} and {@code field_value} have no
 * JPA entity; the catalog assertions check the JDBC mapping those tables use.
 */
@Tag("integration")
@Tag("docker")
class FieldRegistryMigrationIntegrationTest {

    private static final List<String> ENTITY_PACKAGES = List.of(
            "com.opencrm.crm",
            "com.opencrm.common",
            "com.opencrm.audit");

    private static final Set<String> REQUIRED_ENTITIES = Set.of(
            "Organization",
            "Contact",
            "Deal",
            "Lead",
            "Activity",
            "CrmFact",
            "CrmFactEvidence",
            "OutboxEvent",
            "AuditEntry");

    private static final Set<String> REGISTRY_INDEXES = Set.of(
            "field_definition_pkey",
            "uq_field_definition_tenant_entity_key",
            "field_option_pkey",
            "uq_field_option_active_label",
            "field_value_pkey",
            "uq_field_value_organization",
            "uq_field_value_contact",
            "uq_field_value_deal",
            "idx_field_value_text",
            "idx_field_value_numeric",
            "idx_field_value_date",
            "idx_field_value_bool",
            "idx_field_value_option",
            "idx_field_value_user");

    @Test
    void migrationAppliesOnACleanDatabaseAndValidatesTheEntityModel() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:16-alpine")) {
            database.start();
            Flyway flyway = flyway(database);
            assertThat(flyway.info().applied()).isEmpty();

            assertThat(flyway.migrate().migrationsExecuted).isPositive();
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(flyway.info().pending()).isEmpty();

            List<String> versions = new ArrayList<>();
            for (MigrationInfo info : flyway.info().applied()) {
                if (info.getVersion() != null) {
                    versions.add(info.getVersion().getVersion());
                }
            }
            assertThat(versions).endsWith("20", "21");

            try (Connection connection = jdbc(database); Statement statement = connection.createStatement()) {
                assertRegistryCatalog(statement);
                assertConstraintsRejectBadRows(statement);
                assertLongIncompressibleTextInserts(connection);
            }
            assertThat(validatedEntityNames(database)).containsAll(REQUIRED_ENTITIES);
        }
    }

    private static void assertRegistryCatalog(Statement statement) throws SQLException {
        Map<String, Column> columns = columns(statement);
        assertThat(columns.keySet()).containsExactlyInAnyOrderElementsOf(expectedColumns().keySet());
        for (Map.Entry<String, Column> expected : expectedColumns().entrySet()) {
            assertThat(columns.get(expected.getKey()))
                    .as(expected.getKey())
                    .isEqualTo(expected.getValue());
        }
        assertThat(columns.values()).noneMatch(column ->
                column.dataType().equals("json") || column.dataType().equals("jsonb"));

        assertThat(names(statement, """
                SELECT c.conname
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'crm'
                  AND t.relname IN ('field_definition', 'field_option', 'field_value')
                ORDER BY c.conname
                """)).contains(
                "ck_field_definition_entity",
                "ck_field_definition_key",
                "ck_field_definition_label",
                "ck_field_definition_position",
                "ck_field_definition_type",
                "ck_field_option_label",
                "ck_field_option_position",
                "ck_field_value_one_record",
                "ck_field_value_one_storage",
                "fk_field_option_definition",
                "fk_field_value_contact",
                "fk_field_value_deal",
                "fk_field_value_definition",
                "fk_field_value_option",
                "fk_field_value_organization",
                "uq_field_definition_tenant_entity_key");

        Map<String, String> indexes = indexDefinitions(statement);
        assertThat(indexes.keySet()).containsExactlyInAnyOrderElementsOf(REGISTRY_INDEXES);
        assertThat(indexes.get("uq_field_option_active_label")).contains("UNIQUE").contains("archived_at IS NULL");
        assertThat(indexes.get("uq_field_value_organization")).contains("UNIQUE").contains("organization_id IS NOT NULL");
        assertThat(indexes.get("uq_field_value_contact")).contains("UNIQUE").contains("contact_id IS NOT NULL");
        assertThat(indexes.get("uq_field_value_deal")).contains("UNIQUE").contains("deal_id IS NOT NULL");
        assertThat(indexes.get("idx_field_value_numeric")).contains("numeric");
        String textIndex = indexes.get("idx_field_value_text");
        assertThat(textIndex).containsIgnoringCase("left").contains("256");
        assertThat(textIndex).doesNotContain("(definition_id, \"text\")");
        assertThat(textIndex).doesNotContain("(definition_id, text)");
        assertThat(indexes.get("idx_field_value_date")).contains("date");
        assertThat(indexes.get("idx_field_value_bool")).contains("bool");
        assertThat(indexes.get("idx_field_value_option")).contains("option_id");
        assertThat(indexes.get("idx_field_value_user")).contains("user_id");

        String numeric = scalar(statement, """
                SELECT numeric_precision || ',' || numeric_scale
                FROM information_schema.columns
                WHERE table_schema = 'crm' AND table_name = 'field_value' AND column_name = 'numeric'
                """);
        assertThat(numeric).isEqualTo("24,4");
    }

    private static void assertConstraintsRejectBadRows(Statement statement) throws SQLException {
        long organizationId = insertReturningId(statement, """
                INSERT INTO crm.organizations (uuid, name, created_by)
                VALUES ('%s', 'Registry migration', 'test') RETURNING id
                """.formatted(UUID.randomUUID()));
        long definitionId = insertReturningId(statement, """
                INSERT INTO crm.field_definition (
                    tenant, entity, "key", label, type, required, show_on_record, show_on_list, position,
                    agent_filled, created_by, updated_by
                ) VALUES (
                    'tenant-a', 'organization', 'po_box', 'PO Box', 'text', false, true, false, 1,
                    false, 'test', 'test'
                ) RETURNING id
                """);
        assertThat(scalar(statement,
                "SELECT version::text FROM crm.field_definition WHERE id = " + definitionId)).isEqualTo("0");

        assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO crm.field_definition (
                    tenant, entity, "key", label, type, required, show_on_record, show_on_list, position, agent_filled
                ) VALUES (
                    'tenant-a', 'organization', 'po_box', 'Again', 'text', false, true, false, 2, false
                )
                """)).isInstanceOf(SQLException.class).hasMessageContaining("uq_field_definition_tenant_entity_key");

        long optionId = insertReturningId(statement, """
                INSERT INTO crm.field_option (definition_id, label, position, created_by, updated_by)
                VALUES (%d, 'Standard', 1, 'test', 'test') RETURNING id
                """.formatted(definitionId));
        long valueId = insertReturningId(statement, """
                INSERT INTO crm.field_value (definition_id, organization_id, "text", created_by, updated_by)
                VALUES (%d, %d, 'box-1', 'test', 'test') RETURNING id
                """.formatted(definitionId, organizationId));
        assertThat(valueId).isPositive();
        assertThat(optionId).isPositive();

        assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO crm.field_value (definition_id, organization_id, "text")
                VALUES (%d, %d, 'box-2')
                """.formatted(definitionId, organizationId)))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO crm.field_value (definition_id, organization_id, "text", "numeric")
                VALUES (%d, %d, 'both', 1)
                """.formatted(definitionId, organizationId)))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_field_value_one_storage");
        assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO crm.field_value (definition_id, "text")
                VALUES (%d, 'no-record')
                """.formatted(definitionId)))
                .isInstanceOf(SQLException.class).hasMessageContaining("ck_field_value_one_record");
    }

    /**
     * A 20_000-character value is the long-text maximum. Random characters do not
     * shrink, so a btree on the raw column would fail with SQLSTATE 54000.
     */
    private static void assertLongIncompressibleTextInserts(Connection connection) throws SQLException {
        String text = incompressibleText(20_000);
        long organizationId;
        long definitionId;
        try (Statement statement = connection.createStatement()) {
            organizationId = insertReturningId(statement, """
                    INSERT INTO crm.organizations (uuid, name, created_by)
                    VALUES ('%s', 'Long text', 'test') RETURNING id
                    """.formatted(UUID.randomUUID()));
            definitionId = insertReturningId(statement, """
                    INSERT INTO crm.field_definition (
                        tenant, entity, "key", label, type, required, show_on_record, show_on_list, position,
                        agent_filled, created_by, updated_by
                    ) VALUES (
                        'tenant-a', 'organization', 'long_note', 'Long note', 'long_text', false, true, false, 2,
                        false, 'test', 'test'
                    ) RETURNING id
                    """);
        }
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO crm.field_value (definition_id, organization_id, "text", created_by, updated_by)
                VALUES (?, ?, ?, 'test', 'test') RETURNING length("text")
                """)) {
            insert.setLong(1, definitionId);
            insert.setLong(2, organizationId);
            insert.setString(3, text);
            try (ResultSet rows = insert.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(20_000);
            }
        }
        try (PreparedStatement read = connection.prepareStatement("""
                SELECT "text" FROM crm.field_value
                WHERE definition_id = ? AND organization_id = ?
                """)) {
            read.setLong(1, definitionId);
            read.setLong(2, organizationId);
            try (ResultSet rows = read.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(text);
            }
        }
    }

    private static String incompressibleText(int length) {
        Random random = new Random(length);
        char[] chars = new char[length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (0x21 + random.nextInt(0xD7FF - 0x21));
        }
        return new String(chars);
    }

    private static Set<String> validatedEntityNames(PostgreSQLContainer<?> database) throws ClassNotFoundException {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", database.getJdbcUrl())
                .applySetting("hibernate.connection.username", database.getUsername())
                .applySetting("hibernate.connection.password", database.getPassword())
                .applySetting("hibernate.connection.driver_class", "org.postgresql.Driver")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.default_schema", "crm")
                .applySetting("hibernate.hbm2ddl.auto", "validate")
                .applySetting("hibernate.implicit_naming_strategy", SpringImplicitNamingStrategy.class.getName())
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", "true")
                .build();
        MetadataSources sources = new MetadataSources(registry);
        Set<String> names = new TreeSet<>();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (String base : ENTITY_PACKAGES) {
            for (var candidate : scanner.findCandidateComponents(base)) {
                Class<?> type = Class.forName(candidate.getBeanClassName());
                sources.addAnnotatedClass(type);
                names.add(type.getSimpleName());
            }
        }
        assertThat(names).containsAll(REQUIRED_ENTITIES);
        try (SessionFactory factory = sources.buildMetadata().buildSessionFactory()) {
            assertThat(factory.getMetamodel().getEntities()).hasSize(names.size());
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
        return names;
    }

    private static Map<String, Column> columns(Statement statement) throws SQLException {
        Map<String, Column> columns = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery("""
                SELECT table_name, column_name, data_type, character_maximum_length,
                       numeric_precision, numeric_scale, is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'crm'
                  AND table_name IN ('field_definition', 'field_option', 'field_value')
                ORDER BY table_name, ordinal_position
                """)) {
            while (rows.next()) {
                String key = rows.getString("table_name") + "." + rows.getString("column_name");
                Integer length = (Integer) rows.getObject("character_maximum_length");
                Integer precision = (Integer) rows.getObject("numeric_precision");
                Integer scale = (Integer) rows.getObject("numeric_scale");
                columns.put(key, new Column(
                        rows.getString("data_type"),
                        length,
                        precision,
                        scale,
                        "YES".equals(rows.getString("is_nullable"))));
            }
        }
        return columns;
    }

    private static Map<String, String> indexDefinitions(Statement statement) throws SQLException {
        Map<String, String> indexes = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery("""
                SELECT indexname, indexdef
                FROM pg_indexes
                WHERE schemaname = 'crm'
                  AND tablename IN ('field_definition', 'field_option', 'field_value')
                ORDER BY indexname
                """)) {
            while (rows.next()) {
                indexes.put(rows.getString("indexname"), rows.getString("indexdef"));
            }
        }
        return indexes;
    }

    private static List<String> names(Statement statement, String sql) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                names.add(rows.getString(1));
            }
        }
        return names;
    }

    private static String scalar(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }

    private static long insertReturningId(Statement statement, String sql) throws SQLException {
        try (ResultSet rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getLong(1);
        }
    }

    private static Map<String, Column> expectedColumns() {
        Map<String, Column> columns = new LinkedHashMap<>();
        bigint(columns, "field_definition", "id", false);
        varchar(columns, "field_definition", "tenant", 128, false);
        varchar(columns, "field_definition", "entity", 32, false);
        varchar(columns, "field_definition", "key", 64, false);
        varchar(columns, "field_definition", "label", 255, false);
        varchar(columns, "field_definition", "type", 32, false);
        bool(columns, "field_definition", "required", false);
        bool(columns, "field_definition", "show_on_record", false);
        bool(columns, "field_definition", "show_on_list", false);
        integer(columns, "field_definition", "position", false);
        bool(columns, "field_definition", "agent_filled", false);
        text(columns, "field_definition", "agent_brief", true);
        timestamptz(columns, "field_definition", "archived_at", true);
        timestamptz(columns, "field_definition", "created_at", false);
        timestamptz(columns, "field_definition", "updated_at", true);
        varchar(columns, "field_definition", "created_by", 255, true);
        varchar(columns, "field_definition", "updated_by", 255, true);
        bigint(columns, "field_definition", "version", false);

        bigint(columns, "field_option", "id", false);
        bigint(columns, "field_option", "definition_id", false);
        varchar(columns, "field_option", "label", 255, false);
        integer(columns, "field_option", "position", false);
        timestamptz(columns, "field_option", "archived_at", true);
        timestamptz(columns, "field_option", "created_at", false);
        timestamptz(columns, "field_option", "updated_at", true);
        varchar(columns, "field_option", "created_by", 255, true);
        varchar(columns, "field_option", "updated_by", 255, true);
        bigint(columns, "field_option", "version", false);

        bigint(columns, "field_value", "id", false);
        bigint(columns, "field_value", "definition_id", false);
        bigint(columns, "field_value", "organization_id", true);
        bigint(columns, "field_value", "contact_id", true);
        bigint(columns, "field_value", "deal_id", true);
        text(columns, "field_value", "text", true);
        columns.put("field_value.numeric", new Column("numeric", null, 24, 4, true));
        columns.put("field_value.date", new Column("date", null, null, null, true));
        bool(columns, "field_value", "bool", true);
        bigint(columns, "field_value", "option_id", true);
        varchar(columns, "field_value", "user_id", 255, true);
        timestamptz(columns, "field_value", "created_at", false);
        timestamptz(columns, "field_value", "updated_at", true);
        varchar(columns, "field_value", "created_by", 255, true);
        varchar(columns, "field_value", "updated_by", 255, true);
        bigint(columns, "field_value", "version", false);
        return columns;
    }

    private static void bigint(Map<String, Column> columns, String table, String name, boolean nullable) {
        columns.put(table + "." + name, new Column("bigint", null, 64, 0, nullable));
    }

    private static void integer(Map<String, Column> columns, String table, String name, boolean nullable) {
        columns.put(table + "." + name, new Column("integer", null, 32, 0, nullable));
    }

    private static void varchar(Map<String, Column> columns, String table, String name, int length, boolean nullable) {
        columns.put(table + "." + name, new Column("character varying", length, null, null, nullable));
    }

    private static void bool(Map<String, Column> columns, String table, String name, boolean nullable) {
        columns.put(table + "." + name, new Column("boolean", null, null, null, nullable));
    }

    private static void text(Map<String, Column> columns, String table, String name, boolean nullable) {
        columns.put(table + "." + name, new Column("text", null, null, null, nullable));
    }

    private static void timestamptz(Map<String, Column> columns, String table, String name, boolean nullable) {
        columns.put(table + "." + name, new Column("timestamp with time zone", null, null, null, nullable));
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

    private record Column(String dataType, Integer length, Integer precision, Integer scale, boolean nullable) {
    }
}
