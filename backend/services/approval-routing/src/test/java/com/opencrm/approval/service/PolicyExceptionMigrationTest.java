package com.opencrm.approval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V1_13 policy exception migration")
class PolicyExceptionMigrationTest {

    @Test
    @DisplayName("the next free version has a rollback header and does not alter approval rows")
    void migrationIsTheNextVersionAndLeavesApprovalRowsUntouched() throws Exception {
        Path root = moduleRoot();
        String sql = Files.readString(root.resolve("src/main/resources/db/migration/V1_13__policy_exceptions.sql"));
        String rollback = Files.readString(root.resolve("src/main/resources/db/rollback/V1_13__drop_policy_exceptions.sql"));

        assertThat(sql).startsWith("-- Rollback: db/rollback/V1_13__drop_policy_exceptions.sql");
        assertThat(sql).contains("CREATE TABLE policy_exceptions");
        assertThat(sql).doesNotContain("ALTER TABLE");
        assertThat(sql).doesNotContain("TABLE approval_requests");
        assertThat(sql).doesNotContain("UPDATE ");
        assertThat(rollback).contains("DROP TABLE IF EXISTS policy_exceptions");
        assertThat(Files.list(root.resolve("src/main/resources/db/migration"))
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith("V1_13__"))
                .toList()).containsExactly("V1_13__policy_exceptions.sql");
    }

    private static Path moduleRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("src/main/resources/db/migration/V1_13__policy_exceptions.sql"))) {
            return cwd;
        }
        Path nested = cwd.resolve("services/approval-routing");
        if (Files.exists(nested.resolve("src/main/resources/db/migration/V1_13__policy_exceptions.sql"))) {
            return nested;
        }
        throw new AssertionError("approval-routing root not found from " + cwd);
    }
}
