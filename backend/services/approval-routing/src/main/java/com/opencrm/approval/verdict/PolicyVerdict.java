package com.opencrm.approval.verdict;

/**
 * Stand-in for the DRG-442 {@code EvaluateQuote} result: Pass, RequiresApproval, or Blocked.
 *
 * <p>DRG-442 owns the real evaluator and {@code QuoteEvaluated}. This enum is the verdict shape BC-04-03 routes on.
 * Rebase this branch onto the DRG-442 branch and fold the duplicate into that type.
 */
public enum PolicyVerdict {
    PASS,
    REQUIRES_APPROVAL,
    BLOCKED
}
