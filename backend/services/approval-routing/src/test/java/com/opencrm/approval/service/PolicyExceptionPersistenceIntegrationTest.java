package com.opencrm.approval.service;

import com.opencrm.approval.domain.PolicyExceptionGrant;
import com.opencrm.approval.domain.PolicyVerdict;
import com.opencrm.approval.domain.QuotePolicyDecision;
import com.opencrm.approval.domain.QuotePolicyEvaluator;
import com.opencrm.approval.domain.QuoteVersionFacts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exceptions persist in PostgreSQL. The evaluator sees only the rows the quote version and tenant query returns,
 * and an expired row that is returned still fails evaluation.
 */
@Tag("integration")
@DisplayName("policy exception persistence")
class PolicyExceptionPersistenceIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private JdbcTemplate jdbc;

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

    @BeforeEach
    void schema() throws Exception {
        DriverManagerDataSource data = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(data);
        jdbc.execute("DROP TABLE IF EXISTS policy_exceptions");
        jdbc.execute("DROP TABLE IF EXISTS policy_thresholds");
        jdbc.execute("DROP TABLE IF EXISTS commercial_policies");
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V1_10__create_commercial_policy_tables.sql")));
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V1_13__policy_exceptions.sql")));
        Long policyId = jdbc.queryForObject("""
                INSERT INTO commercial_policies (uuid, name, status, policy_version, created_at, version, is_deleted)
                VALUES ('11111111-1111-4111-8111-111111111111', 'Enterprise', 'ACTIVE', 1, NOW(), 0, FALSE)
                RETURNING id
                """, Long.class);
        insert(policyId, "tenant-1", 3L, Instant.parse("2026-12-01T00:00:00Z"));
        insert(policyId, "tenant-1", 3L, Instant.parse("2026-11-01T00:00:00Z"));
        insert(policyId, "tenant-1", 2L, Instant.parse("2026-12-01T00:00:00Z"));
        insert(policyId, "tenant-2", 3L, Instant.parse("2026-12-01T00:00:00Z"));
    }

    @Test
    @DisplayName("active same-version exception passes; expired and wrong-version rows do not")
    void persistedExceptionsHonorOnlyTheActiveSameVersion() {
        assertThat(jdbc.queryForObject("SELECT to_regclass('approval_requests')", String.class)).isNull();

        Instant now = Instant.parse("2026-11-24T18:00:00Z");
        List<PolicyExceptionGrant> sameVersion = load("42", 3L, "tenant-1");
        assertThat(sameVersion).hasSize(2);
        com.opencrm.approval.domain.CommercialPolicy policy = policy(sameVersion.getFirst().policyId());

        QuotePolicyDecision honored = QuotePolicyEvaluator.evaluateQuote(
                facts(), List.of(policy), sameVersion, now);
        assertThat(honored.verdict()).isEqualTo(PolicyVerdict.PASS);

        List<PolicyExceptionGrant> expiredOnly = sameVersion.stream()
                .filter(grant -> !grant.expiresAt().isAfter(now))
                .toList();
        assertThat(expiredOnly).hasSize(1);
        QuotePolicyDecision expired = QuotePolicyEvaluator.evaluateQuote(
                facts(), List.of(policy), expiredOnly, now);
        assertThat(expired.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);

        List<PolicyExceptionGrant> wrongVersion = load("42", 2L, "tenant-1");
        assertThat(wrongVersion).singleElement().satisfies(grant -> assertThat(grant.quoteVersion()).isEqualTo(2L));
        QuotePolicyDecision otherVersion = QuotePolicyEvaluator.evaluateQuote(
                facts(), List.of(policy), wrongVersion, now);
        assertThat(otherVersion.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);

        assertThat(load("42", 3L, "tenant-2")).singleElement()
                .satisfies(grant -> assertThat(grant.tenantId()).isEqualTo("tenant-2"));
    }

    private List<PolicyExceptionGrant> load(String quoteId, long quoteVersion, String tenantId) {
        return jdbc.query("""
                SELECT quote_id, quote_version, policy_id, tenant_id, expires_at
                FROM policy_exceptions
                WHERE quote_id = ? AND quote_version = ? AND tenant_id = ? AND is_deleted = FALSE
                """, (rs, row) -> new PolicyExceptionGrant(
                rs.getString("quote_id"),
                rs.getLong("quote_version"),
                rs.getLong("policy_id"),
                rs.getString("tenant_id"),
                rs.getTimestamp("expires_at").toInstant()), quoteId, quoteVersion, tenantId);
    }

    private int sequence;

    private void insert(Long policyId, String tenantId, long quoteVersion, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO policy_exceptions
                    (uuid, tenant_id, quote_id, quote_version, policy_id, justification, expires_at, recorded_by, created_at, version, is_deleted)
                VALUES (?, ?, '42', ?, ?, 'one-off', ?, 'user:desk', NOW(), 0, FALSE)
                """, "22222222-2222-4222-8222-" + String.format("%012d", ++sequence),
                tenantId, quoteVersion, policyId, java.sql.Timestamp.from(expiresAt));
    }

    private static QuoteVersionFacts facts() {
        return new QuoteVersionFacts(
                "42", 3L, "tenant-1", "Enterprise", "Software", "AMER",
                new BigDecimal("32"), new BigDecimal("40"), 12, List.of(), LocalDate.of(2026, 11, 24));
    }

    private static com.opencrm.approval.domain.CommercialPolicy policy(long id) {
        com.opencrm.approval.domain.CommercialPolicy policy = com.opencrm.approval.domain.CommercialPolicy.builder()
                .name("Enterprise")
                .status(com.opencrm.approval.domain.PolicyStatus.ACTIVE)
                .policyVersion(1)
                .segment("Enterprise")
                .productFamily("Software")
                .region("AMER")
                .effectiveDate(LocalDate.of(2026, 11, 23))
                .expirationDate(LocalDate.of(2026, 11, 25))
                .build();
        policy.setId(id);
        policy.addThreshold(com.opencrm.approval.domain.PolicyThreshold.builder()
                .thresholdType(com.opencrm.approval.domain.ThresholdType.DISCOUNT_PERCENT)
                .limitValue(new BigDecimal("25"))
                .build());
        return policy;
    }
}
