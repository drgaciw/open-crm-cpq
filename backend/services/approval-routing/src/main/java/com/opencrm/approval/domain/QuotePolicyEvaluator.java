package com.opencrm.approval.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code EvaluateQuote(quoteVersion)} against the commercial policies defined by DRG-441.
 *
 * <p>Discount over the maximum, term over the maximum, and a restricted clause require approval.
 * A margin below the floor is blocked: the floor is a value the deal must retain, not an escalation band.
 * A threshold whose actual value was not measured is blocked, so an absent fact cannot pass.
 * No applicable policy is a pass. Deprecated, deleted, out-of-window, and out-of-scope policies are ignored.
 * This does not choose an approver or write an approval request.
 */
public final class QuotePolicyEvaluator {

    private QuotePolicyEvaluator() {
    }

    public static QuotePolicyDecision evaluateQuote(QuoteVersionFacts quoteVersion, List<CommercialPolicy> policies) {
        List<PolicyBreach> breaches = new ArrayList<>();
        List<Long> applied = new ArrayList<>();
        List<CommercialPolicy> applicable = policies == null ? List.of() : policies;
        for (CommercialPolicy policy : applicable) {
            if (!applies(policy, quoteVersion)) {
                continue;
            }
            if (policy.getId() != null) {
                applied.add(policy.getId());
            }
            List<PolicyThreshold> thresholds = policy.getThresholds() == null ? List.of() : policy.getThresholds();
            for (PolicyThreshold threshold : thresholds) {
                if (threshold == null || threshold.isDeleted() || threshold.getThresholdType() == null) {
                    continue;
                }
                PolicyBreach breach = breach(policy, threshold, quoteVersion);
                if (breach != null) {
                    breaches.add(breach);
                }
            }
        }
        return new QuotePolicyDecision(verdict(breaches), breaches, applied);
    }

    private static PolicyVerdict verdict(List<PolicyBreach> breaches) {
        PolicyVerdict verdict = PolicyVerdict.PASS;
        for (PolicyBreach breach : breaches) {
            if (breach.effect() == BreachEffect.BLOCKED) {
                return PolicyVerdict.BLOCKED;
            }
            verdict = PolicyVerdict.REQUIRES_APPROVAL;
        }
        return verdict;
    }

    private static boolean applies(CommercialPolicy policy, QuoteVersionFacts facts) {
        if (policy == null || policy.isDeleted() || policy.getStatus() != PolicyStatus.ACTIVE) {
            return false;
        }
        if (!inForce(policy, facts.asOf())) {
            return false;
        }
        return matches(policy.getSegment(), facts.segment())
                && matches(policy.getProductFamily(), facts.productFamily())
                && matches(policy.getRegion(), facts.region());
    }

    /** A null or blank policy dimension applies to every quote. A null quote dimension matches every policy. */
    private static boolean matches(String policyValue, String quoteValue) {
        if (policyValue == null || policyValue.isBlank()) {
            return true;
        }
        if (quoteValue == null || quoteValue.isBlank()) {
            return true;
        }
        return policyValue.trim().equalsIgnoreCase(quoteValue.trim());
    }

    private static boolean inForce(CommercialPolicy policy, LocalDate asOf) {
        LocalDate start = policy.getEffectiveDate();
        LocalDate end = policy.getExpirationDate();
        if (asOf == null) {
            return start == null && end == null;
        }
        if (start != null && asOf.isBefore(start)) {
            return false;
        }
        return end == null || !asOf.isAfter(end);
    }

    private static PolicyBreach breach(CommercialPolicy policy, PolicyThreshold threshold, QuoteVersionFacts facts) {
        ThresholdType type = threshold.getThresholdType();
        return switch (type) {
            case DISCOUNT_PERCENT -> numeric(
                    policy, threshold, facts.discountPercent(), true, BreachEffect.REQUIRES_APPROVAL);
            case TERM_MONTHS -> numeric(
                    policy,
                    threshold,
                    facts.termMonths() == null ? null : BigDecimal.valueOf(facts.termMonths()),
                    true,
                    BreachEffect.REQUIRES_APPROVAL);
            case MARGIN_FLOOR -> numeric(
                    policy, threshold, facts.marginPercent(), false, BreachEffect.BLOCKED);
            case NON_STANDARD_CLAUSE -> clause(policy, threshold, facts);
        };
    }

    /**
     * @param overLimit true when the threshold is a maximum (discount, term); false when it is a minimum (margin)
     */
    private static PolicyBreach numeric(
            CommercialPolicy policy,
            PolicyThreshold threshold,
            BigDecimal actual,
            boolean overLimit,
            BreachEffect whenBreached) {
        BigDecimal limit = threshold.getLimitValue();
        if (actual == null || limit == null) {
            return breach(policy, threshold, actual == null ? "unmeasured" : plain(actual),
                    limit == null ? "unmeasured" : plain(limit), BreachEffect.BLOCKED);
        }
        boolean breached = overLimit ? actual.compareTo(limit) > 0 : actual.compareTo(limit) < 0;
        if (!breached) {
            return null;
        }
        return breach(policy, threshold, plain(actual), plain(limit), whenBreached);
    }

    private static PolicyBreach clause(CommercialPolicy policy, PolicyThreshold threshold, QuoteVersionFacts facts) {
        String restricted = threshold.getClauseCode();
        if (restricted == null || restricted.isBlank()) {
            return breach(policy, threshold, "unmeasured", "unmeasured", BreachEffect.BLOCKED);
        }
        String present = null;
        for (String code : facts.clauseCodes()) {
            if (code != null && code.trim().equalsIgnoreCase(restricted.trim())) {
                present = code.trim();
                break;
            }
        }
        if (present == null) {
            return null;
        }
        return breach(policy, threshold, present, restricted.trim(), BreachEffect.REQUIRES_APPROVAL);
    }

    private static PolicyBreach breach(
            CommercialPolicy policy,
            PolicyThreshold threshold,
            String actual,
            String limit,
            BreachEffect effect) {
        return new PolicyBreach(
                policy.getId(),
                policy.getPolicyVersion(),
                threshold.getThresholdType(),
                actual,
                limit,
                effect);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
