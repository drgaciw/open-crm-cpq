package com.opencrm.approval.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The H4 migration is a new file. Applied migrations are not edited. V1_14 owns the tenant_id column and its status index. */
class ApprovalTenantMigrationTest {

    @Test
    void migrationBackfillsFromTheOwningQuoteAndHasARollbackScript() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V1_15__approval_request_tenant.sql");
        Path rollback = Path.of("src/main/resources/db/rollback/V1_15__drop_approval_request_tenant.sql");
        String sql = Files.readString(migration);
        String undo = Files.readString(rollback);

        assertThat(sql).contains("-- Rollback: db/rollback/V1_15__drop_approval_request_tenant.sql");
        assertThat(sql).contains("ALTER COLUMN tenant_id TYPE VARCHAR(128);");
        assertThat(sql).doesNotContain("ADD COLUMN tenant_id");
        assertThat(sql).doesNotContain("CREATE INDEX idx_approval_tenant_status");
        assertThat(sql).contains("ADD COLUMN account_id VARCHAR(255)");
        assertThat(sql).contains("quote.quotes");
        assertThat(sql).contains("customer_external_id");
        assertThat(sql).contains("not derivable");
        assertThat(sql).contains("to_regclass('quote.quotes')");
        assertThat(sql).contains("ar.tenant_id IS NULL");
        assertThat(sql).doesNotContain("VARCHAR(128) NOT NULL");
        assertThat(undo).doesNotContain("DROP COLUMN IF EXISTS tenant_id");
        assertThat(undo).doesNotContain("idx_approval_tenant_status;");
        assertThat(undo).contains("DROP COLUMN IF EXISTS account_id");
        assertThat(Files.readString(Path.of("src/main/resources/db/migration/V1_11__approval_submit_receipts.sql")))
                .doesNotContain("tenant_id");
    }
}
