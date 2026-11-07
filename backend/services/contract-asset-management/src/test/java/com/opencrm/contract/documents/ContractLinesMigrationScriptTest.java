package com.opencrm.contract.documents;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-665 AC1/AC2 — Docker-free guard for the contract_lines migration.
 * Failing-first: asserts the next free CAM slot (V1_16 at launch) exists and
 * stamps tenant_id from the parent contract, never from a caller body.
 */
@Tag("unit")
class ContractLinesMigrationScriptTest {

    @Test
    void v116DeclaresContractLinesWithTenantFromContract() throws IOException {
        String sql = script("db/migration/V1_16__create_contract_lines.sql");
        String body = sql.replaceAll("(?m)--.*$", "");

        assertThat(body).contains("CREATE TABLE contract_lines");
        assertThat(body).contains("tenant_id uuid NOT NULL");
        assertThat(body).contains("contract_id uuid NOT NULL");
        assertThat(body).contains("REFERENCES contracts");
        assertThat(body).contains("accepted_line_uuid");
        assertThat(body).contains("uq_contract_lines_contract_line_no");
        assertThat(body).contains("uq_contract_lines_tenant_accepted_line");
        assertThat(body).contains("contract_lines_stamp_tenant_from_contract");
        assertThat(body).doesNotContain("SubscriptionActivated");
        assertThat(body).doesNotContain("EntitlementProvisioned");
        assertThat(sql.toLowerCase()).contains("rollback");
    }

    @Test
    void v117AddsNullableSubscriptionContractLineIdSeparately() throws IOException {
        String sql = script("db/migration/V1_17__subscriptions_contract_line_id.sql");
        String body = sql.replaceAll("(?m)--.*$", "");

        assertThat(body).contains("ADD COLUMN contract_line_id");
        assertThat(body).contains("REFERENCES contract_lines");
        assertThat(body).doesNotContain("CREATE TABLE contract_lines");
        assertThat(body).doesNotContain("ALTER COLUMN quote_id");
        assertThat(body).doesNotContain("DROP NOT NULL");
        assertThat(sql.toLowerCase()).contains("rollback");
    }

    @Test
    void v117RollbackRefusesWhileSubscriptionsCarryContractLineLinks() throws IOException {
        String rollback = script("db/rollback/V1_17__drop_subscriptions_contract_line_id.sql");
        String body = rollback.replaceAll("(?m)--.*$", "");

        assertThat(body).contains("contract_line_id IS NOT NULL");
        assertThat(body).contains("refusing to drop subscriptions.contract_line_id");
        assertThat(body).contains("DROP COLUMN contract_line_id");
        assertThat(body).contains("version = '1.17'");
        assertThat(body).doesNotContain("DROP TABLE");
    }

    @Test
    void v116RollbackRefusesWhileV117LinkColumnRemainsOrRowsExist() throws IOException {
        String rollback = script("db/rollback/V1_16__drop_contract_lines.sql");
        String body = rollback.replaceAll("(?m)--.*$", "");

        assertThat(body).contains("refusing to drop contract_lines: subscriptions.contract_line_id still present");
        assertThat(body).contains("refusing to drop contract_lines: % row(s) present");
        assertThat(body).contains("DROP TABLE contract_lines");
        assertThat(body).contains("version = '1.16'");
        assertThat(body.indexOf("subscriptions.contract_line_id still present"))
                .as("V1_17 guard must run before the table is dropped")
                .isLessThan(body.indexOf("DROP TABLE contract_lines"));
    }

    private static String script(String path) throws IOException {
        try (var in = ContractLinesMigrationScriptTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("expected resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
