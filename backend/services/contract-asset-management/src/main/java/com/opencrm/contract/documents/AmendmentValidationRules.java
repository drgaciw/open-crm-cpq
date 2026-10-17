package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.BusinessCategory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** V1–V14 for the first slice. V12 and V13 are structural here; the apply ledger is S4. */
public final class AmendmentValidationRules {

    private static final Set<ActionCode> SLICE_ACTIONS = EnumSet.of(ActionCode.ADD, ActionCode.UPDATE, ActionCode.REMOVE);
    private static final Set<ActionCode> SOURCE_REQUIRED = EnumSet.of(
            ActionCode.UPDATE, ActionCode.REMOVE, ActionCode.SWAP, ActionCode.UPGRADE, ActionCode.DOWNGRADE);

    private AmendmentValidationRules() {
    }

    public static BusinessCategory categoryFor(ActionCode action, BigDecimal deltaQty) {
        return switch (action) {
            case ADD -> BusinessCategory.UPSELL;
            case UPDATE -> {
                int sign = deltaQty.signum();
                if (sign > 0) {
                    yield BusinessCategory.UPSELL;
                }
                if (sign < 0) {
                    yield BusinessCategory.DOWNSELL;
                }
                yield BusinessCategory.TERMS_AND_CONDITIONS;
            }
            case REMOVE -> BusinessCategory.CANCELLATION;
            default -> throw new IllegalArgumentException("no first-slice category for " + action);
        };
    }

    public static List<RuleFailure> onSubmit(SubmitFacts facts) {
        List<RuleFailure> failures = new ArrayList<>();
        if (!"AMEND".equals(facts.intentName) || !SLICE_ACTIONS.contains(facts.action) || !facts.targetOnContract
                || facts.category != categoryFor(facts.action, facts.deltaQty)) {
            failures.add(rule("V1", "intent, action, category or contract target is not valid for this slice"));
        }
        if (!"ACTIVE".equals(facts.subscriptionStatus)) {
            failures.add(rule("V2", "target subscription is not ACTIVE at the action date"));
        }
        if (facts.observedRevision == null || facts.commercialRevision == null
                || !facts.observedRevision.equals(facts.commercialRevision)) {
            failures.add(rule("V3", "observed commercial revision does not match the subscription"));
        }
        if (facts.duplicateSource || (SOURCE_REQUIRED.contains(facts.action) && !facts.sourcePresent)) {
            failures.add(rule("V4", "source accepted line is missing or duplicated"));
        }
        if (facts.lineStart.isBefore(facts.contractStart)
                || (facts.contractEnd != null && facts.lineStart.isAfter(facts.contractEnd))
                || (facts.contractEnd != null && (facts.lineEnd == null || !facts.lineEnd.equals(facts.contractEnd)))) {
            failures.add(rule("V5", "line dates are outside the contract or do not co-term"));
        }
        v6(facts.actionDate, facts.effectiveDate, facts.backdatingReason).ifPresent(failures::add);
        if (facts.semantics == null || facts.startQty == null || facts.deltaQty == null || facts.endQty == null
                || facts.startQty.add(facts.deltaQty).compareTo(facts.endQty) != 0
                || facts.endQty.signum() < 0) {
            failures.add(rule("V7", "quantity semantics do not reconcile"));
        }
        if (!facts.policyPresent || !facts.totalsPresent) {
            failures.add(rule("V8", "priced policy decision or computed totals are missing"));
        }
        if (!facts.pinsPresent) {
            failures.add(rule("V9", "product, pricebook or configuration pin is missing"));
        }
        if (facts.hasRemove && !facts.removeAfterAdd) {
            failures.add(rule("V12", "a remove unit is not ordered after the create unit"));
        }
        if (!facts.unitKeysUnique) {
            failures.add(rule("V13", "application unit key is not unique"));
        }
        if (facts.overrideRequested && !facts.overrideComplete) {
            failures.add(rule("V14", "a manual override needs actor, reason and value"));
        }
        return failures;
    }

    public static List<RuleFailure> computedTotals(BigDecimal tcv, BigDecimal arr, BigDecimal oneTime, BigDecimal credit) {
        if (tcv == null || arr == null || oneTime == null || credit == null) {
            return List.of(rule("V8", "a computed total is unknown"));
        }
        return List.of();
    }

    public static List<RuleFailure> onExecute(ExecuteFacts facts) {
        List<RuleFailure> failures = new ArrayList<>();
        if (!facts.policyPresent || !facts.totalsPresent) {
            failures.add(rule("V8", "priced policy decision or computed totals are missing"));
        }
        if (!facts.pinsPresent) {
            failures.add(rule("V9", "product, pricebook or configuration pin is missing"));
        }
        if (!facts.digestMatches) {
            failures.add(rule("V10", "content digest does not match the executed revision"));
        }
        if (facts.executedRevisionAlreadyExists) {
            failures.add(rule("V11", "a revision is already EXECUTED"));
        }
        return failures;
    }

    public static Optional<RuleFailure> v6(java.time.LocalDate action, java.time.LocalDate effective, String reason) {
        if (effective != null && action != null && effective.isBefore(action) && (reason == null || reason.isBlank())) {
            return Optional.of(rule("V6", "effective date before action date requires a backdating reason code"));
        }
        return Optional.empty();
    }

    private static RuleFailure rule(String id, String message) {
        return new RuleFailure(id, message);
    }
}
