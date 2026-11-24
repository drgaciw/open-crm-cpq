package com.opencrm.approval.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps approvals whose SLA window has elapsed and advances them on {@link EscalationPath}.
 * Disabled when {@code opencrm.approvals.escalation.sweep-enabled} is false. Jev flags are unrelated
 * and stay off.
 * <p>
 * This is a system path. It runs on a scheduler thread with no security context, across every tenant, and
 * must stay off the caller-scoped reads and the decision checks: those refuse a missing caller (401) and,
 * with Security H4, a missing tenant (403 {@code TENANT_REQUIRED}). Person-triggered
 * {@link ApprovalService#escalate(Long)} keeps those checks. {@code ApprovalEscalationIntegrationTest}
 * pins this with dev mode off.
 */
@Component
@ConditionalOnProperty(prefix = "opencrm.approvals.escalation", name = "sweep-enabled", havingValue = "true")
public class ApprovalEscalationJob {

    private final ApprovalService approvals;

    public ApprovalEscalationJob(ApprovalService approvals) {
        this.approvals = approvals;
    }

    @Scheduled(fixedDelayString = "${opencrm.approvals.escalation.sweep-delay-ms:60000}")
    public void sweep() {
        approvals.escalateDue();
    }
}
