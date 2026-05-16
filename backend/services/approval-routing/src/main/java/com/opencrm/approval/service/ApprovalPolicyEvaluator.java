package com.opencrm.approval.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApprovalPolicyEvaluator {

    private static final List<String> APPROVAL_TIERS = List.of(
            "SALES_MANAGER",
            "DEAL_DESK",
            "VP_SALES",
            "CFO"
    );

    private final Clock clock;

    public ApprovalPolicyEvaluator() {
        this(Clock.systemUTC());
    }

    ApprovalPolicyEvaluator(Clock clock) {
        this.clock = clock;
    }

    public ApprovalPolicyDecision evaluate(ApprovalPolicyRequest request) {
        List<String> reasons = new ArrayList<>();
        int highestTierIndex = -1;

        highestTierIndex = requireAtLeast(highestTierIndex, evaluateQuoteAmount(request.quoteTotalAmount(), reasons));
        highestTierIndex = requireAtLeast(highestTierIndex, evaluateDiscount(request.maximumDiscountPercent(), reasons));
        highestTierIndex = requireAtLeast(highestTierIndex, evaluateMargin(request.minimumGrossMarginPercent(), reasons));

        if (highestTierIndex < 0) {
            return new ApprovalPolicyDecision(
                    false,
                    null,
                    List.of(),
                    List.of(),
                    false,
                    null,
                    reasons
            );
        }

        List<String> approvalChain = new ArrayList<>(APPROVAL_TIERS.subList(0, highestTierIndex + 1));
        boolean separationOfDutyViolation = request.requesterApprovalTier() != null
                && approvalChain.contains(request.requesterApprovalTier());
        List<String> eligibleApproverTiers = new ArrayList<>(approvalChain);

        if (separationOfDutyViolation) {
            eligibleApproverTiers.remove(request.requesterApprovalTier());
            reasons.add("SEPARATION_OF_DUTY_REQUESTER_IN_CHAIN");
        }

        return new ApprovalPolicyDecision(
                true,
                APPROVAL_TIERS.get(highestTierIndex),
                approvalChain,
                eligibleApproverTiers,
                separationOfDutyViolation,
                clock.instant().plus(Duration.ofHours(24)),
                List.copyOf(reasons)
        );
    }

    public ApprovalEscalationDecision evaluateEscalation(ApprovalEscalationRequest request) {
        if (!"PENDING".equals(request.status()) || request.slaDeadline().isAfter(clock.instant())) {
            return new ApprovalEscalationDecision(false, null, null, null);
        }

        int currentTierIndex = APPROVAL_TIERS.indexOf(request.currentTier());
        String escalationTier = currentTierIndex >= 0 && currentTierIndex < APPROVAL_TIERS.size() - 1
                ? APPROVAL_TIERS.get(currentTierIndex + 1)
                : request.currentTier();

        return new ApprovalEscalationDecision(true, escalationTier, clock.instant(), "SLA_EXPIRED");
    }

    private int evaluateQuoteAmount(BigDecimal quoteTotalAmount, List<String> reasons) {
        if (isAtLeast(quoteTotalAmount, "1000000")) {
            reasons.add("QUOTE_TOTAL_AT_LEAST_1000000");
            return 3;
        }
        if (isAtLeast(quoteTotalAmount, "500000")) {
            reasons.add("QUOTE_TOTAL_AT_LEAST_500000");
            return 2;
        }
        if (isAtLeast(quoteTotalAmount, "100000")) {
            reasons.add("QUOTE_TOTAL_AT_LEAST_100000");
            return 0;
        }
        return -1;
    }

    private int evaluateDiscount(BigDecimal maximumDiscountPercent, List<String> reasons) {
        if (isAtLeast(maximumDiscountPercent, "25")) {
            reasons.add("MAX_DISCOUNT_AT_LEAST_25");
            return 3;
        }
        if (isAtLeast(maximumDiscountPercent, "20")) {
            reasons.add("MAX_DISCOUNT_AT_LEAST_20");
            return 2;
        }
        if (isAtLeast(maximumDiscountPercent, "15")) {
            reasons.add("MAX_DISCOUNT_AT_LEAST_15");
            return 1;
        }
        return -1;
    }

    private int evaluateMargin(BigDecimal minimumGrossMarginPercent, List<String> reasons) {
        if (minimumGrossMarginPercent == null) {
            return -1;
        }
        if (minimumGrossMarginPercent.compareTo(new BigDecimal("20")) < 0) {
            reasons.add("MIN_GROSS_MARGIN_BELOW_20");
            return 3;
        }
        if (minimumGrossMarginPercent.compareTo(new BigDecimal("35")) < 0) {
            reasons.add("MIN_GROSS_MARGIN_BELOW_35");
            return 1;
        }
        return -1;
    }

    private int requireAtLeast(int current, int candidate) {
        return Math.max(current, candidate);
    }

    private boolean isAtLeast(BigDecimal actual, String threshold) {
        return actual != null && actual.compareTo(new BigDecimal(threshold)) >= 0;
    }
}
