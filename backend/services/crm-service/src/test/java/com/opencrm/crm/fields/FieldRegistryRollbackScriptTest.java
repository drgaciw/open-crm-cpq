package com.opencrm.crm.fields;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free guard for the V21 rollback. The script lives beside the Flyway
 * migrations and must stay off every configured migration location.
 */
@Tag("unit")
class FieldRegistryRollbackScriptTest {

    @Test
    void scriptDropsRegistryObjectsInReverseDependencyOrder() throws IOException {
        String sql = resource("db/rollback/V21__drop_field_registry.sql");
        String body = sql.replaceAll("(?m)--.*$", "");

        int fieldValue = body.indexOf("DROP TABLE IF EXISTS field_value");
        int fieldOption = body.indexOf("DROP TABLE IF EXISTS field_option");
        int fieldDefinition = body.indexOf("DROP TABLE IF EXISTS field_definition");

        assertThat(fieldValue).isNotNegative();
        assertThat(fieldValue).isLessThan(fieldOption);
        assertThat(fieldOption).isLessThan(fieldDefinition);
        assertThat(body.indexOf("DROP INDEX IF EXISTS idx_field_value_text")).isLessThan(fieldValue);
        assertThat(body.indexOf("DROP INDEX IF EXISTS uq_field_value_organization")).isLessThan(fieldValue);
        assertThat(body.indexOf("DROP INDEX IF EXISTS uq_field_option_active_label")).isLessThan(fieldValue);
        assertThat(body.indexOf("DROP CONSTRAINT IF EXISTS fk_field_value_definition")).isLessThan(fieldValue);
        assertThat(body.indexOf("DROP CONSTRAINT IF EXISTS fk_field_option_definition")).isLessThan(fieldValue);
        assertThat(body.indexOf("DROP CONSTRAINT IF EXISTS uq_field_definition_tenant_entity_key")).isLessThan(fieldValue);
        assertThat(body).contains("SET search_path TO crm");
        assertThat(body).doesNotContain("saved_view");
        assertThat(body).doesNotContain("DROP TABLE organizations");
        assertThat(body).doesNotContain("DROP TABLE contacts");
        assertThat(body).doesNotContain("DROP TABLE deals");
        assertThat(body).doesNotContain("flyway_schema_history");
        assertThat(sql).contains("DATA-DESTRUCTIVE");
    }

    @Test
    void flywayLocationsStayOnTheMigrationDirectory() throws IOException {
        String main = resource("application.yml");
        String test = resource("application-test.yml");

        assertThat(main).contains("locations: classpath:db/migration");
        assertThat(test).contains("locations: classpath:db/migration");
        assertThat(main).doesNotContain("db/rollback");
        assertThat(test).doesNotContain("db/rollback");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = FieldRegistryRollbackScriptTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
