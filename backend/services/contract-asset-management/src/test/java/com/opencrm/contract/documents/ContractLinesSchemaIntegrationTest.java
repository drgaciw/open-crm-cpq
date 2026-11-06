package com.opencrm.contract.documents;

import com.opencrm.contract.ContractAssetManagementApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-665 — Flyway applies contract_lines and nullable subscriptions.contract_line_id;
 * DB trigger stamps tenant_id from the parent contract.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class ContractLinesSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=contract");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void contractLinesTableAndNullableSubscriptionFkExist() {
        Map<String, String> columns = new HashMap<>();
        jdbc.query("""
                SELECT column_name, is_nullable, data_type
                FROM information_schema.columns
                WHERE table_schema = 'contract' AND table_name = 'contract_lines'
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> columns.put(
                rs.getString("column_name"),
                rs.getString("is_nullable") + " " + rs.getString("data_type")));
        assertThat(columns).containsEntry("tenant_id", "NO uuid");
        assertThat(columns).containsEntry("contract_id", "NO uuid");
        assertThat(columns).containsEntry("accepted_line_uuid", "NO character varying");
        assertThat(columns).containsEntry("status", "NO character varying");

        String subFk = jdbc.queryForObject("""
                SELECT is_nullable || ' ' || data_type FROM information_schema.columns
                WHERE table_schema = 'contract' AND table_name = 'subscriptions'
                  AND column_name = 'contract_line_id'
                """, String.class);
        assertThat(subFk).isEqualTo("YES uuid");

        String quoteNullability = jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'contract' AND table_name = 'subscriptions'
                  AND column_name = 'quote_id'
                """, String.class);
        assertThat(quoteNullability).isEqualTo("NO");
    }

    @Test
    void triggerStampsTenantFromContractIgnoringSuppliedTenant() {
        UUID contractTenant = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        UUID smuggledTenant = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        UUID contractId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");
        UUID lineId = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");
        UUID accountId = UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd");

        jdbc.update("""
                INSERT INTO contracts (
                    id, tenant_id, contract_number, customer_account_id, term_model,
                    start_date, end_date, status, last_executed_seq, created_by, version)
                VALUES (?, ?, 'C-665', ?, 'TERMED', DATE '2026-01-01', DATE '2026-12-31',
                        'ACTIVE', 0, 'it', 0)
                """, contractId, contractTenant, accountId);

        jdbc.update("""
                INSERT INTO contract_lines (
                    id, tenant_id, contract_id, line_no, accepted_line_uuid, product_external_id,
                    quantity, status, effective_from, charge_type, created_by, version)
                VALUES (?, ?, ?, 1, 'line-665', 'SKU-1', 1, 'PENDING', DATE '2026-01-01',
                        'RECURRING', 'it', 0)
                """, lineId, smuggledTenant, contractId);

        UUID storedTenant = jdbc.queryForObject(
                "SELECT tenant_id FROM contract_lines WHERE id = ?", UUID.class, lineId);
        assertThat(storedTenant).isEqualTo(contractTenant);
        assertThat(storedTenant).isNotEqualTo(smuggledTenant);
    }

}
