package com.opencrm.approval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Approval Policy Evaluator")
class ApprovalPolicyEvaluatorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-12T12:00:00Z"), ZoneOffset.UTC);
    private final ApprovalPolicyEvaluator evaluator = new ApprovalPolicyEvaluator(clock);

    @Test
    @DisplayName("routes a quote to every required level up to the highest breached threshold")
    void routesToEveryRequiredLevelUpToHighestBreachedThreshold() {
        ApprovalPolicyDecision decision = evaluator.evaluate(ApprovalPolicyRequest.builder()
                .quoteTotalAmount(new BigDecimal("750000.00"))
                .maximumDiscountPercent(new BigDecimal("22.5"))
                .minimumGrossMarginPercent(new BigDecimal("18.0"))
                .requesterId("rep-1")
                .build());

        assertEquals("CFO", decision.requiredTier());
        assertEquals(
                java.util.List.of("SALES_MANAGER", "DEAL_DESK", "VP_SALES", "CFO"),
                decision.requiredApprovalChain()
        );
        assertEquals(Instant.parse("2026-05-13T12:00:00Z"), decision.slaDeadline());
        assertTrue(decision.policyReasons().contains("MIN_GROSS_MARGIN_BELOW_20"));
    }

    @Test
    @DisplayName("blocks separation-of-duty violations when requester is in the approval chain")
    void blocksSeparationOfDutyViolations() {
        ApprovalPolicyDecision decision = evaluator.evaluate(ApprovalPolicyRequest.builder()
                .quoteTotalAmount(new BigDecimal("250000.00"))
                .maximumDiscountPercent(new BigDecimal("16.0"))
                .minimumGrossMarginPercent(new BigDecimal("34.0"))
                .requesterId("manager-1")
                .requesterApprovalTier("SALES_MANAGER")
                .build());

        assertTrue(decision.requiresApproval());
        assertTrue(decision.separationOfDutyViolation());
        assertFalse(decision.eligibleApproverTiers().contains("SALES_MANAGER"));
        assertTrue(decision.policyReasons().contains("SEPARATION_OF_DUTY_REQUESTER_IN_CHAIN"));
    }

    @Test
    @DisplayName("escalates expired pending approvals to the next tier")
    void escalatesExpiredPendingApprovals() {
        ApprovalEscalationDecision decision = evaluator.evaluateEscalation(ApprovalEscalationRequest.builder()
                .currentTier("DEAL_DESK")
                .slaDeadline(clock.instant().minus(Duration.ofMinutes(1)))
                .status("PENDING")
                .build());

        assertTrue(decision.shouldEscalate());
        assertEquals("VP_SALES", decision.escalationTier());
        assertEquals(clock.instant(), decision.escalatedAt());
        assertEquals("SLA_EXPIRED", decision.reasonCode());
    }
}
