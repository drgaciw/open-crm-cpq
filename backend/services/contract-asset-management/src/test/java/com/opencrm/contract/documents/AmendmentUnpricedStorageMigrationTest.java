package com.opencrm.contract.documents;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** V1_10 is the next free CAM version. V1_9 stays unused. The rollback statement refuses null rows. */
class AmendmentUnpricedStorageMigrationTest {

    @Test
    void v110RelaxesUnpricedColumnsAndCarriesAGuardedRollback() throws IOException {
        String sql;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V1_10__amendment_unpriced_revision_storage.sql")) {
            assertThat(in).as("V1_10 migration").isNotNull();
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String executable = sql.replaceAll("(?m)--.*$", "");
        assertThat(executable).contains("ALTER TABLE amendment_documents ALTER COLUMN content_digest DROP NOT NULL");
        assertThat(executable).contains("ALTER TABLE amendment_documents ALTER COLUMN tcv_delta DROP NOT NULL");
        assertThat(executable).contains("ALTER TABLE amendment_documents ALTER COLUMN arr_delta DROP NOT NULL");
        assertThat(executable).contains("ALTER TABLE amendment_documents ALTER COLUMN one_time_total DROP NOT NULL");
        assertThat(executable).contains("ALTER TABLE amendment_documents ALTER COLUMN credit_total DROP NOT NULL");
        assertThat(executable).contains(
                "ALTER TABLE amendment_document_revisions ALTER COLUMN content_digest DROP NOT NULL");
        assertThat(executable).contains(
                "ALTER TABLE amendment_document_lines ALTER COLUMN product_external_id DROP NOT NULL");
        assertThat(executable).contains(
                "ALTER TABLE amendment_document_lines ALTER COLUMN pricebook_version DROP NOT NULL");
        assertThat(executable).contains(
                "ALTER TABLE amendment_document_lines ALTER COLUMN configuration_hash DROP NOT NULL");
        assertThat(executable).contains("CREATE TABLE amendment_commercial_envelopes");
        assertThat(executable).contains("CREATE TABLE amendment_operation_receipts");
        assertThat(executable).contains("tenant_id, document_id, revision_no, envelope_digest");
        assertThat(executable).contains("CREATE OR REPLACE FUNCTION amendment_unpriced_storage_v1_10_rollback");
        assertThat(executable).contains("affected row remains null");
        assertThat(executable).contains("not fabricated");
        assertThat(executable).doesNotContain("SELECT amendment_unpriced_storage_v1_10_rollback");
        assertThat(executable).doesNotContain("document_apply_ledger");
        assertThat(sql).doesNotContain("V1_7_3");
        assertThat(sql).doesNotContain("V1_9");
    }
}
