package com.opencrm.approval.service;

import java.time.Instant;

public record ApprovalEscalationDecision(
        boolean shouldEscalate,
        String escalationTier,
        Instant escalatedAt,
        String reasonCode
) {
}
