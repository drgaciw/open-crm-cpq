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
            String code = actual == null ? unknownCode(threshold.getThresholdType()) : "THRESHOLD_LIMIT_UNKNOWN";
            return breach(policy, threshold, actual == null ? "unmeasured" : plain(actual),
                    limit == null ? "unmeasured" : plain(limit), BreachEffect.BLOCKED, code);
        }
        boolean breached = overLimit ? actual.compareTo(limit) > 0 : actual.compareTo(limit) < 0;
        if (!breached) {
            return null;
        }
        String code = whenBreached == BreachEffect.BLOCKED ? blockCode(threshold.getThresholdType()) : null;
        return breach(policy, threshold, plain(actual), plain(limit), whenBreached, code);
    }

    private static PolicyBreach clause(CommercialPolicy policy, PolicyThreshold threshold, QuoteVersionFacts facts) {
        String restricted = threshold.getClauseCode();
        if (restricted == null || restricted.isBlank()) {
            return breach(policy, threshold, "unmeasured", "unmeasured", BreachEffect.BLOCKED, "CLAUSE_UNKNOWN");
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
        return breach(policy, threshold, present, restricted.trim(), BreachEffect.REQUIRES_APPROVAL, null);
    }

    /** A missing measurement. Margin uses the owner-confirmed code {@code MARGIN_UNKNOWN}. */
    private static String unknownCode(ThresholdType type) {
        return switch (type) {
            case MARGIN_FLOOR -> "MARGIN_UNKNOWN";
            case DISCOUNT_PERCENT -> "DISCOUNT_UNKNOWN";
            case TERM_MONTHS -> "TERM_UNKNOWN";
            case NON_STANDARD_CLAUSE -> "CLAUSE_UNKNOWN";
        };
    }

    /** A measured value that fails a hard stop. */
    private static String blockCode(ThresholdType type) {
        return switch (type) {
            case MARGIN_FLOOR -> "MARGIN_BELOW_FLOOR";
            case DISCOUNT_PERCENT -> "DISCOUNT_PERCENT";
            case TERM_MONTHS -> "TERM_MONTHS";
            case NON_STANDARD_CLAUSE -> "NON_STANDARD_CLAUSE";
        };
    }

    private static PolicyBreach breach(
            CommercialPolicy policy,
            PolicyThreshold threshold,
            String actual,
            String limit,
            BreachEffect effect,
            String reasonCode) {
        return new PolicyBreach(
                policy.getId(),
                policy.getPolicyVersion(),
                threshold.getThresholdType(),
                actual,
                limit,
                effect,
                reasonCode);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
