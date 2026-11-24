package com.opencrm.approval.testdoubles;

/**
 * Result of an escalation check. {@code fired} is false while the SLA window is still open.
 * After the window, {@code assigneeId} is the next step on the path.
 */
public record Escalation(boolean fired, String assigneeId) {
}
