package com.opencrm.crm.fields;

import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free guard for the DRG-514a registry migration. CRM versions on main run
 * {@code V1} through {@code V20} with no {@code V1_xx} scripts. Flyway sorts an
 * underscore version such as {@code 1.1} before {@code 2}, so the next in-order
 * version after {@code 20} is {@code 21}.
 */
class FieldRegistryMigrationTest {

    @Test
    void versionTwentyOneIsTheNextIntegerAfterEveryExistingCrmMigration() throws IOException, URISyntaxException {
        List<String> names = migrationNames();

        assertThat(names).contains("V20__audit_entries.sql", "V21__field_registry.sql");
        assertThat(names).allMatch(name -> name.matches("V\\d+__.+\\.sql"));
        assertThat(names.stream().map(FieldRegistryMigrationTest::versionOf).distinct()).hasSameSizeAs(names);

        MigrationVersion previousMax = names.stream()
                .filter(name -> !name.startsWith("V21__"))
                .map(FieldRegistryMigrationTest::versionOf)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        assertThat(previousMax).isEqualTo(MigrationVersion.fromVersion("20"));
        assertThat(versionOf("V21__field_registry.sql")).isEqualTo(MigrationVersion.fromVersion("21"));
        assertThat(versionOf("V21__field_registry.sql").compareTo(previousMax)).isPositive();
    }

    @Test
    void scriptMatchesTheJdbcRegistryAndAdr031() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V21__field_registry.sql")) {
            assertThat(in).as("V21 field registry migration").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("CREATE TABLE field_definition");
        assertThat(sql).contains("CREATE TABLE field_option");
        assertThat(sql).contains("CREATE TABLE field_value");
        assertThat(sql).contains("CONSTRAINT uq_field_definition_tenant_entity_key UNIQUE (tenant, entity, \"key\")");
        assertThat(sql).contains("\"numeric\" NUMERIC(24,4)");
        assertThat(sql).contains("CREATE UNIQUE INDEX uq_field_option_active_label");
        assertThat(sql).contains("CREATE UNIQUE INDEX uq_field_value_organization");
        assertThat(sql).contains("CREATE UNIQUE INDEX uq_field_value_contact");
        assertThat(sql).contains("CREATE UNIQUE INDEX uq_field_value_deal");
        assertThat(sql).contains("CREATE INDEX idx_field_value_text");
        assertThat(sql).contains("CREATE INDEX idx_field_value_numeric");
        assertThat(sql).contains("CREATE INDEX idx_field_value_date");
        assertThat(sql).contains("CREATE INDEX idx_field_value_bool");
        assertThat(sql).contains("CREATE INDEX idx_field_value_option");
        assertThat(sql).contains("CREATE INDEX idx_field_value_user");
        assertThat(sql).contains("REFERENCES organizations (id)");
        assertThat(sql).contains("REFERENCES contacts (id)");
        assertThat(sql).contains("REFERENCES deals (id)");
        assertThat(sql).doesNotContain("jsonb");
        assertThat(sql).doesNotContain("CREATE TABLE saved_view");
        assertThat(sql).doesNotContain("IF NOT EXISTS");
        assertThat(sql).doesNotContain("${");
    }

    private static List<String> migrationNames() throws IOException, URISyntaxException {
        Path source = Path.of(FieldRegistryMigrationTest.class.getClassLoader()
                .getResource("db/migration").toURI());
        try (Stream<Path> files = Files.list(source)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".sql"))
                    .sorted()
                    .toList();
        }
    }

    private static MigrationVersion versionOf(String filename) {
        int separator = filename.indexOf("__");
        assertThat(separator).isGreaterThan(1);
        return MigrationVersion.fromVersion(filename.substring(1, separator).replace('_', '.'));
    }
}
