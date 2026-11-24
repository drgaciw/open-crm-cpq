package com.opencrm.approval.service;

import com.opencrm.approval.ApprovalRoutingApplication;
import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyThreshold;
import com.opencrm.approval.domain.PolicyVerdict;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.dto.QuoteEvaluationDto;
import com.opencrm.approval.dto.QuoteEvaluationRequest;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.security.test.TestJwtDecoderConfiguration;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * QuoteEvaluated is persisted on the outbox and the BC-20 audit trail (Testcontainers PostgreSQL).
 */
@Tag("integration")
@Testcontainers
@Import(TestJwtDecoderConfiguration.class)
@SpringBootTest(
        classes = ApprovalRoutingApplication.class,
        properties = {
            "opencrm.security.jwt.issuer-uri=" + TestTokens.ISSUER,
            "spring.kafka.listener.auto-startup=false",
            "spring.autoconfigure.exclude="
                    + "com.azure.spring.cloud.autoconfigure.implementation.context.AzureTokenCredentialAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.compatibility.AzureCompatibilityVerifierAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.aadb2c.configuration.AadB2cResourceServerAutoConfiguration,"
                    + "com.azure.spring.cloud.autoconfigure.implementation.kafka.AzureEventHubsKafkaOAuth2AutoConfiguration"
        })
@DisplayName("EvaluateQuote persists QuoteEvaluated evidence")
class QuoteEvaluationEvidenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1")
            .withEnv("KAFKA_HEAP_OPTS", "-Xms128m -Xmx256m");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=approval");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private QuotePolicyEvaluationService service;
    @Autowired
    private CommercialPolicyRepository policies;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearActor() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void requiresApprovalIsStoredOnTheOutboxAndTheAuditTrail() {
        CommercialPolicy policy = CommercialPolicy.builder().name("Standard").policyVersion(1).build();
        policy.addThreshold(PolicyThreshold.builder()
                .thresholdType(ThresholdType.DISCOUNT_PERCENT)
                .limitValue(new BigDecimal("25"))
                .build());
        policies.saveAndFlush(policy);

        asUser("tenant-1");
        QuoteEvaluationDto verdict = service.evaluate(QuoteEvaluationRequest.builder()
                .quoteId("quote-442")
                .quoteVersion(3L)
                .tenantId("tenant-1")
                .discountPercent(new BigDecimal("32"))
                .marginPercent(new BigDecimal("40"))
                .termMonths(12)
                .build());

        assertThat(verdict.getVerdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(verdict.getBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.getActual()).isEqualTo("32");
            assertThat(breach.getLimit()).isEqualTo("25");
        });

        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox_events WHERE aggregate_type = 'Quote' AND aggregate_id = 'quote-442'"
                        + " AND topic = 'cpq.approval.quote.evaluated'",
                String.class);
        assertThat(payload).contains("REQUIRES_APPROVAL").contains("32").contains("25");

        Map<String, Object> audit = jdbc.queryForMap(
                "SELECT action, principal, tenant_id, details FROM audit_entries"
                        + " WHERE aggregate_type = 'Quote' AND aggregate_id = 'quote-442'");
        assertThat(audit.get("action")).isEqualTo("QuoteEvaluated");
        assertThat(audit.get("principal")).isEqualTo("user:seller-oid");
        assertThat(audit.get("tenant_id")).isEqualTo("tenant-1");
        assertThat(audit.get("details").toString()).contains("32").contains("25");
    }

    private static void asUser(String tenantId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .header("alg", "none")
                .subject("seller")
                .claim("oid", "seller-oid")
                .claim("tid", tenantId)
                .claim("scp", "access_as_user")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_CPQ_USER"))));
    }
}
