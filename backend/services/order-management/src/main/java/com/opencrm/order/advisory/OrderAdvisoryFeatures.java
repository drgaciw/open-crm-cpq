package com.opencrm.order.advisory;

import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.state.JevState;
import java.time.Duration;

/** Finite, privacy-safe exception facts. Subject identifiers and reasons never enter this contract. */
public final class OrderAdvisoryFeatures {
    private OrderAdvisoryFeatures() {}

    public static final JevFeatureSchema SCHEMA = JevFeatureSchema.named("oms-exception-triage", 1)
            .code("category").code("severity").code("ageBand").code("unknownOutcome")
            .code("pendingEvidence").code("conflictingEvidence").code("activationPending")
            .code("financePending").build();

    public enum Category {
        UNKNOWN, UNKNOWN_OUTCOME, ACK_DEADLINE, ATTEMPTS_EXHAUSTED, ALLOCATION_EXPIRED,
        PENDING_DELIVERY, CONTRADICTORY_EVIDENCE, OUTBOX_EXHAUSTED, FINANCE_DISCREPANCY,
        FINANCE_CONTRADICTORY, FINANCE_UNKNOWN, FINANCE_REJECTED, TERMINAL_INCONSISTENCY,
        DEPENDENCY_INCONSISTENCY, PARTIAL_RECURRING_CANCELLATION, CONTRADICTORY_ACK, REVIEW_NOTE;

        /** Unknown database codes are discarded, never forwarded verbatim. */
        public static Category from(String code) {
            if (code == null) return UNKNOWN;
            try { return valueOf(code); } catch (IllegalArgumentException ignored) { return UNKNOWN; }
        }
    }

    public enum Severity {
        UNKNOWN, LOW, MEDIUM, HIGH, CRITICAL;
        public static Severity from(String code) {
            if (code == null) return UNKNOWN;
            try { return valueOf(code); } catch (IllegalArgumentException ignored) { return UNKNOWN; }
        }
    }

    public enum Flag {
        UNKNOWN, YES, NO;
        public static Flag of(Boolean value) { return value == null ? UNKNOWN : value ? YES : NO; }
    }

    public enum AgeBand {
        UNKNOWN, UNDER_HOUR, HOURS_1_TO_24, DAYS_1_TO_7, DAYS_7_PLUS;
        public static AgeBand from(Duration age) {
            if (age == null || age.isNegative()) return UNKNOWN;
            if (age.compareTo(Duration.ofHours(1)) < 0) return UNDER_HOUR;
            if (age.compareTo(Duration.ofDays(1)) < 0) return HOURS_1_TO_24;
            if (age.compareTo(Duration.ofDays(7)) < 0) return DAYS_1_TO_7;
            return DAYS_7_PLUS;
        }
    }

    public record Facts(Category category, Severity severity, AgeBand ageBand, Flag unknownOutcome,
                        Flag pendingEvidence, Flag conflictingEvidence, Flag activationPending,
                        Flag financePending) {
        public Facts {
            category = category == null ? Category.UNKNOWN : category;
            severity = severity == null ? Severity.UNKNOWN : severity;
            ageBand = ageBand == null ? AgeBand.UNKNOWN : ageBand;
            unknownOutcome = unknownOutcome == null ? Flag.UNKNOWN : unknownOutcome;
            pendingEvidence = pendingEvidence == null ? Flag.UNKNOWN : pendingEvidence;
            conflictingEvidence = conflictingEvidence == null ? Flag.UNKNOWN : conflictingEvidence;
            activationPending = activationPending == null ? Flag.UNKNOWN : activationPending;
            financePending = financePending == null ? Flag.UNKNOWN : financePending;
        }
    }

    public static JevState state(Facts facts) {
        if (facts == null) facts = new Facts(null, null, null, null, null, null, null, null);
        return JevState.builder(SCHEMA).put("category", facts.category())
                .put("severity", facts.severity()).put("ageBand", facts.ageBand())
                .put("unknownOutcome", facts.unknownOutcome()).put("pendingEvidence", facts.pendingEvidence())
                .put("conflictingEvidence", facts.conflictingEvidence())
                .put("activationPending", facts.activationPending()).put("financePending", facts.financePending())
                .build();
    }
}
