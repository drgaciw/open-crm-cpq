package com.opencrm.contract.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** DRG-662 AC1: CAM V1_18 adds nullable subscriptions.tenant_id with header rollback. */
@Tag("unit")
class SubscriptionTenantMigrationTest {

    @Test
    void v118AddsNullableUuidTenantWithRollbackComment() throws IOException {
        String migration = resource("db/migration/V1_18__subscription_tenant_id.sql");
        String executable = migration.replaceAll("(?m)--.*$", "");

        assertThat(executable).contains("ALTER TABLE subscriptions");
        assertThat(executable).contains("ADD COLUMN tenant_id uuid");
        assertThat(executable.toLowerCase()).doesNotContain("not null");
        assertThat(executable.toLowerCase()).doesNotContain("backfill");
        assertThat(migration).contains("Rollback is manual");
        assertThat(migration).contains("ALTER TABLE subscriptions DROP COLUMN IF EXISTS tenant_id;");
        assertThat(headerRollbackStatements(migration))
                .containsExactly("ALTER TABLE subscriptions DROP COLUMN IF EXISTS tenant_id;");
        assertThat(migration).contains("Entra");
        assertThat(migration).doesNotContain("V1_13__");
        assertThat(migration).doesNotContain("V1_16__");
    }

    private static List<String> headerRollbackStatements(String sql) {
        String marker = "Apply in the contract schema:";
        int start = sql.indexOf(marker);
        assertThat(start).isGreaterThan(0);
        List<String> statements = new ArrayList<>();
        for (String raw : sql.substring(start + marker.length()).split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            if (!line.startsWith("--")) {
                break;
            }
            String body = line.substring(2).strip();
            if (body.endsWith(";")) {
                statements.add(body);
            }
        }
        return statements;
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = SubscriptionTenantMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
