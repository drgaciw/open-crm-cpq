package com.opencrm.approval.service;

import java.time.Instant;
import java.util.List;

public record ApprovalPolicyDecision(
        boolean requiresApproval,
        String requiredTier,
        List<String> requiredApprovalChain,
        List<String> eligibleApproverTiers,
        boolean separationOfDutyViolation,
        Instant slaDeadline,
        List<String> policyReasons
) {
}
