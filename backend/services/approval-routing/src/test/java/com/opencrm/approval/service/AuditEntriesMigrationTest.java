package com.opencrm.approval.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEntriesMigrationTest {

    @Test
    void rollbackHeaderNamesTheScriptInThisModule() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V1_12__audit_entries.sql"));
        String header = sql.lines().filter(line -> line.startsWith("-- Rollback:")).findFirst().orElseThrow();
        String relative = header.substring("-- Rollback:".length()).trim();
        assertThat(relative).isEqualTo("db/rollback/V1_12__drop_audit_entries.sql");
        assertThat(Path.of("src/main/resources").resolve(relative)).exists();
        assertThat(Files.readString(Path.of("src/main/resources").resolve(relative))).contains("DROP TABLE");
    }
}
