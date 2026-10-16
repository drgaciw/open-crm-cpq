package com.opencrm.contract.documents;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Docker-free guard: V1_8 stays a header backfill and does not take V1_9. */
class ContractHeaderBackfillScriptTest {

    @Test
    void migrationInstallsTheBackfillAndDoesNotInventATenantOrHistory() throws IOException {
        String sql = script();
        assertThat(sql).contains(
                "One thin contract per existing subscription. No synthetic amendment history and no");
        assertThat(sql).contains("retroactive events. A later merge, tracked on DRG-554, is the place to regroup.");
        assertThat(sql).contains("The backfill does not regroup.");
        assertThat(sql).contains("'" + ContractHeaderBackfill.BACKFILL_ACTOR + "'");
        assertThat(sql).contains("'" + ContractHeaderBackfill.CREATION_ACTOR + "'");
        assertThat(sql).contains(ContractHeaderBackfill.TENANT_REQUIRED);
        assertThat(sql).contains(ContractHeaderBackfill.NOT_ELIGIBLE);
        assertThat(sql).contains(ContractHeaderBackfill.NUMBER_TAKEN);
        assertThat(sql).contains(ContractHeaderBackfill.RESTORE_FROM_BACKUP);
        assertThat(sql).contains("contract_header_backfill_count");
        assertThat(sql).contains("contract_header_backfill_apply");
        assertThat(sql).contains("contract_header_at_creation");
        assertThat(sql).contains("contract_header_backfill_compensate");
        assertThat(sql).contains("reject_frozen_amendment_line_mutation");
        assertThat(sql).contains("ON CONFLICT (tenant_id, contract_number) DO NOTHING");
        assertThat(sql).doesNotContain("PERFORM contract_header_backfill_apply");
        assertThat(sql).doesNotContain("SELECT contract_header_backfill_apply");
        assertThat(sql).doesNotContain("INSERT INTO amendment_document");
        assertThat(sql).doesNotContain("INSERT INTO outbox_events");
        assertThat(sql).doesNotContain("INSERT INTO subscription_rate_periods");
        assertThat(sql).doesNotContain("INSERT INTO applied_quote_acceptances");
        assertThat(sql).doesNotContain("UPDATE amendment_document_lines");
        assertThat(sql).doesNotContain("DELETE FROM amendment_document_lines");
        assertThat(sql).doesNotContain("precedence_flag");
        assertThat(sql).doesNotContain("CREATE TABLE document_apply_ledger");
        assertThat(sql).doesNotContain("V1_9__");
        assertThat(sql).doesNotContainPattern("(?i)values\\s*\\(\\s*'[0-9a-f]{8}-[0-9a-f]{4}-");
    }

    private static String script() throws IOException {
        try (var in = ContractHeaderBackfillScriptTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V1_8__contract_header_backfill.sql")) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
