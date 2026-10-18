package com.opencrm.contract.documents.apply;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** DoD v0.3 Core 5. V1_12 is the next free CAM version. V1_9 is not created. */
@Tag("unit")
class DocumentApplyLedgerMigrationTest {

    @Test
    void v112CreatesTheLedgerAndTheRollbackRefusesWhenRowsExist() throws IOException {
        String migration = resource("db/migration/V1_12__create_document_apply_ledger.sql");
        String executable = migration.replaceAll("(?m)--.*$", "");
        assertThat(executable).contains("CREATE TABLE document_apply_ledger");
        assertThat(executable).contains("tenant_id uuid NOT NULL");
        assertThat(executable).contains("PRIMARY KEY (document_id, unit_no)");
        assertThat(executable).contains("apply_key_digest varchar(64) NOT NULL");
        assertThat(executable).contains("UNIQUE (document_id, unit_no, apply_key_digest)");
        assertThat(executable).doesNotContain("applied_quote_acceptances");
        assertThat(migration).doesNotContain("V1_9__");

        String rollback = resource("db/rollback/V1_12__drop_document_apply_ledger.sql");
        assertThat(rollback).contains("document_apply_ledger");
        assertThat(rollback).contains("refusing to drop document_apply_ledger");
        assertThat(rollback).contains("version = '1.12'");
        assertThat(rollback).doesNotContain("applied_quote_acceptances");
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = DocumentApplyLedgerMigrationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
