package com.opencrm.approval.domain;

import java.util.List;

/**
 * {@code GetVerdict} plus {@code ExplainPolicyBreach} for one quote version.
 */
public record QuotePolicyDecision(
        PolicyVerdict verdict,
        List<PolicyBreach> breaches,
        List<Long> appliedPolicyIds) {

    public QuotePolicyDecision {
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
        appliedPolicyIds = appliedPolicyIds == null ? List.of() : List.copyOf(appliedPolicyIds);
    }

    /** Each breached threshold, with the quote's actual value and the policy limit. */
    public List<PolicyBreach> explainPolicyBreaches() {
        return breaches;
    }
}
