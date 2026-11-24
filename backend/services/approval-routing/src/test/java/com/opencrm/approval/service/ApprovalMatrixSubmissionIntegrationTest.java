package com.opencrm.approval.service;

import com.opencrm.approval.ApprovalRoutingApplication;
import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.dto.ApprovalMatrixDto;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.DefineApprovalMatrixRequest;
import com.opencrm.approval.dto.MatrixRuleRequest;
import com.opencrm.approval.dto.PolicyBreachRequest;
import com.opencrm.approval.dto.SubmitForApprovalRequest;
import com.opencrm.approval.domain.PolicyVerdict;
import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL: define a matrix, submit a route, and read the approver inbox. */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ApprovalRoutingApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.security.mode=dev",
        "opencrm.outbox.relay.enabled=false",
        "opencrm.jev.enabled=false",
        "opencrm.jev.discount-tier-shadow.enabled=false"
})
class ApprovalMatrixSubmissionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=approval");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "4");
    }

    @Autowired
    private ApprovalMatrixService matrices;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void signOut() {
        MatrixSessions.clear();
    }

    @Test
    void submitPersistsSerialRouteAndInboxHidesTheWaitingStep() {
        MatrixSessions.user("desk", MatrixSessions.TENANT_A, "DealDesk");
        ApprovalMatrixDto defined = matrices.define(DefineApprovalMatrixRequest.builder()
                .name("Serial discount")
                .rules(List.of(
                        rule(1, MatrixStepMode.SERIAL, QuoteApprovalTier.DEAL_DESK),
                        rule(2, MatrixStepMode.SERIAL, QuoteApprovalTier.SALES_VP)))
                .build());
        assertThat(defined.getMatrixVersion()).isEqualTo(1);

        MatrixSessions.user("seller", MatrixSessions.TENANT_A, "CPQ.User");
        List<ApprovalRequestDto> created = matrices.submitForApproval(submit(defined.getId()));
        assertThat(created).extracting(ApprovalRequestDto::getStatus)
                .containsExactly(com.opencrm.approval.domain.ApprovalStatus.PENDING,
                        com.opencrm.approval.domain.ApprovalStatus.PENDING);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE topic = 'cpq.approval.requested' AND payload LIKE ?",
                Integer.class, "%\"quoteId\":\"42\"%")).isEqualTo(1);

        MatrixSessions.user("desk", MatrixSessions.TENANT_A, "DealDesk");
        assertThat(inboxForQuote("42")).extracting(ApprovalRequestDto::getApprovalTier)
                .containsExactly("DEAL_DESK");

        MatrixSessions.user("vp", MatrixSessions.TENANT_A, "SalesVP");
        assertThat(inboxForQuote("42")).extracting(ApprovalRequestDto::getApprovalTier)
                .containsExactly("SALES_VP");

        MatrixSessions.user("other", MatrixSessions.TENANT_B, "DealDesk");
        assertThat(matrices.listPendingApprovals(null, null)).isEmpty();
        assertThatThrownBy(() -> matrices.submitForApproval(SubmitForApprovalRequest.builder()
                .quoteId("42")
                .quoteVersion("7")
                .verdict(PolicyVerdict.REQUIRES_APPROVAL)
                .quoteTenantId(MatrixSessions.TENANT_A)
                .matrixId(defined.getId())
                .breaches(List.of(PolicyBreachRequest.builder()
                        .breachType("DISCOUNT_PERCENT")
                        .amount(new BigDecimal("25000"))
                        .build()))
                .build()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("CROSS_TENANT");
    }

    @Test
    void parallelSubmitShowsBothRolesInTheInbox() {
        MatrixSessions.user("desk", MatrixSessions.TENANT_A, "DealDesk");
        ApprovalMatrixDto defined = matrices.define(DefineApprovalMatrixRequest.builder()
                .name("Parallel discount " + System.nanoTime())
                .rules(List.of(
                        rule(1, MatrixStepMode.PARALLEL, QuoteApprovalTier.DEAL_DESK),
                        rule(1, MatrixStepMode.PARALLEL, QuoteApprovalTier.CFO)))
                .build());

        MatrixSessions.user("seller", MatrixSessions.TENANT_A, "CPQ.User");
        List<ApprovalRequestDto> created = matrices.submitForApproval(SubmitForApprovalRequest.builder()
                .quoteId("99")
                .quoteVersion("1")
                .verdict(PolicyVerdict.REQUIRES_APPROVAL)
                .quoteTenantId(MatrixSessions.TENANT_A)
                .matrixId(defined.getId())
                .breaches(List.of(PolicyBreachRequest.builder()
                        .breachType("DISCOUNT_PERCENT")
                        .amount(new BigDecimal("25000"))
                        .build()))
                .build());
        assertThat(created).hasSize(2).allMatch(row -> row.getStatus() == com.opencrm.approval.domain.ApprovalStatus.PENDING);

        MatrixSessions.user("desk", MatrixSessions.TENANT_A, "DealDesk");
        assertThat(inboxForQuote("99")).extracting(ApprovalRequestDto::getApprovalTier)
                .containsExactly("DEAL_DESK");

        MatrixSessions.user("cfo", MatrixSessions.TENANT_A, "CFO");
        assertThat(matrices.listPendingApprovals("CFO", null)).extracting(ApprovalRequestDto::getApprovalTier)
                .containsExactly("CFO");
    }

    private List<ApprovalRequestDto> inboxForQuote(String quoteId) {
        return matrices.listPendingApprovals(null, null).stream()
                .filter(row -> quoteId.equals(row.getQuoteId()))
                .toList();
    }

    private static SubmitForApprovalRequest submit(Long matrixId) {
        return SubmitForApprovalRequest.builder()
                .quoteId("42")
                .quoteVersion("7")
                .verdict(PolicyVerdict.REQUIRES_APPROVAL)
                .quoteTenantId(MatrixSessions.TENANT_A)
                .matrixId(matrixId)
                .breaches(List.of(PolicyBreachRequest.builder()
                        .breachType("DISCOUNT_PERCENT")
                        .amount(new BigDecimal("25000"))
                        .build()))
                .build();
    }

    private static MatrixRuleRequest rule(int order, MatrixStepMode mode, QuoteApprovalTier role) {
        return MatrixRuleRequest.builder()
                .breachType(ThresholdType.DISCOUNT_PERCENT)
                .amountMin(BigDecimal.ZERO)
                .amountMax(new BigDecimal("100000"))
                .stepOrder(order)
                .stepMode(mode)
                .approverRole(role)
                .build();
    }
}
