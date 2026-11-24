package com.opencrm.approval.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies V1_15 on PostgreSQL. Rows whose owning quote has a tenant are backfilled.
 * A quote with no tenant, a non-numeric quote id, or no quote row stays null.
 */
@Tag("integration")
class ApprovalTenantMigrationIntegrationTest {

    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void database() {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine");
        postgres.start();
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void backfillCopiesDerivableQuoteTenantAndAccountAndRollbackDropsTheColumns() throws Exception {
        DriverManagerDataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(data);
        jdbc.execute("CREATE SCHEMA quote");
        jdbc.execute("CREATE SCHEMA approval");
        jdbc.execute("SET search_path TO approval");
        jdbc.execute("""
                CREATE TABLE approval_requests (
                    id BIGSERIAL PRIMARY KEY,
                    quote_id VARCHAR(255) NOT NULL,
                    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
                    tenant_id VARCHAR(64)
                )
                """);
        // The table as V1_14 (approval matrix) leaves it: tenant_id VARCHAR(64) and its status index.
        jdbc.execute("CREATE INDEX idx_approval_tenant_status ON approval_requests (tenant_id, status)");
        jdbc.execute("""
                CREATE TABLE quote.quotes (
                    id BIGSERIAL PRIMARY KEY,
                    tenant_id VARCHAR(128),
                    customer_external_id VARCHAR(255),
                    is_deleted BOOLEAN NOT NULL DEFAULT FALSE
                )
                """);
        jdbc.update("INSERT INTO quote.quotes (id, tenant_id, customer_external_id) VALUES (10, 'tenant-a', 'acct-a')");
        jdbc.update("INSERT INTO quote.quotes (id, tenant_id, customer_external_id) VALUES (11, NULL, 'acct-b')");
        jdbc.update("INSERT INTO quote.quotes (id, tenant_id, customer_external_id, is_deleted) VALUES (12, 'tenant-b', 'acct-c', TRUE)");
        jdbc.update("INSERT INTO approval_requests (quote_id) VALUES ('10')");
        jdbc.update("INSERT INTO approval_requests (quote_id) VALUES ('11')");
        jdbc.update("INSERT INTO approval_requests (quote_id) VALUES ('12')");
        jdbc.update("INSERT INTO approval_requests (quote_id) VALUES ('99')");
        jdbc.update("INSERT INTO approval_requests (quote_id) VALUES ('Q-NO')");

        String sql = Files.readString(Path.of("src/main/resources/db/migration/V1_15__approval_request_tenant.sql"));
        jdbc.execute(sql);
        assertThat(jdbc.queryForObject("""
                SELECT character_maximum_length FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'approval_requests' AND column_name = 'tenant_id'
                """, Integer.class)).as("V1_15 widens V1_14's tenant_id").isEqualTo(128);

        Map<String, Object> fromQuote = row(jdbc, "10");
        assertThat(fromQuote).containsEntry("tenant_id", "tenant-a").containsEntry("account_id", "acct-a");
        Map<String, Object> accountOnly = row(jdbc, "11");
        assertThat(accountOnly).containsEntry("tenant_id", null).containsEntry("account_id", "acct-b");
        assertThat(row(jdbc, "12")).containsEntry("tenant_id", null).containsEntry("account_id", null);
        assertThat(row(jdbc, "99")).containsEntry("tenant_id", null).containsEntry("account_id", null);
        assertThat(row(jdbc, "Q-NO")).containsEntry("tenant_id", null).containsEntry("account_id", null);

        List<String> visible = jdbc.queryForList(
                "SELECT quote_id FROM approval_requests WHERE tenant_id = 'tenant-a' ORDER BY quote_id",
                String.class);
        assertThat(visible).containsExactly("10");
        assertThat(jdbc.queryForList(
                "SELECT quote_id FROM approval_requests WHERE tenant_id = 'tenant-a' AND account_id = 'acct-b'",
                String.class)).isEmpty();

        jdbc.update("UPDATE approval_requests SET tenant_id = 'pinned', account_id = 'keep' WHERE quote_id = '10'");
        jdbc.execute(sql.substring(sql.indexOf("DO $$")));
        assertThat(row(jdbc, "10")).containsEntry("tenant_id", "pinned").containsEntry("account_id", "keep");

        jdbc.execute(Files.readString(Path.of("src/main/resources/db/rollback/V1_15__drop_approval_request_tenant.sql")));
        assertThat(columns(jdbc, "account_id")).as("V1_15 rollback drops account_id").isZero();
        assertThat(columns(jdbc, "tenant_id")).as("tenant_id belongs to V1_14 and stays").isOne();
        Integer statusIndex = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = 'idx_approval_tenant_status'",
                Integer.class);
        assertThat(statusIndex).as("V1_14's status index stays").isOne();
    }

    private static Integer columns(JdbcTemplate jdbc, String column) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'approval_requests' AND column_name = ?
                """, Integer.class, column);
    }

    private static Map<String, Object> row(JdbcTemplate jdbc, String quoteId) {
        return jdbc.queryForMap("SELECT tenant_id, account_id FROM approval_requests WHERE quote_id = ?", quoteId);
    }
}
