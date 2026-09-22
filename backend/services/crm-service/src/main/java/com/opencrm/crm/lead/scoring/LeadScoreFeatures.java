package com.opencrm.crm.lead.scoring;

import com.opencrm.crm.lead.Lead;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.question.JevQuestions;
import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.state.JevState;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * What lead scoring may send to Jev (ADR-015): coarse, derived facts only. Title, description, source
 * detail, campaign, next action, lost reason, owner, and every contact or organization field stay in the
 * service. {@code source} is free text in the UI, so it is mapped onto a fixed vocabulary rather than
 * normalised — whatever a user typed can never reach the payload.
 */
final class LeadScoreFeatures {

    static final JevFeatureSchema SCHEMA = JevFeatureSchema.named("lead-score", 1)
            .code("source")
            .code("priority")
            .code("status")
            .code("estimated_value_band")
            .code("currency")
            .bool("has_contact")
            .bool("has_organization")
            .integer("days_since_created")
            .integer("days_since_last_contacted")
            .bool("has_next_action_date")
            .build();

    static final String QUESTION_ID = "qualify";

    /** Rubric {@code lead-score-v1}. Five ascending levels; index 0 is the lowest. */
    static final JevQuestion.Score QUESTION = JevQuestions.score(QUESTION_ID,
            "How likely is this sales lead to become qualified?",
            List.of(
                    "Very unlikely: no estimated value, no engagement, stale",
                    "Unlikely: little value or engagement",
                    "Uncertain: mixed or missing signals",
                    "Likely: meaningful value and recent engagement",
                    "Very likely: high value, recently contacted, next action scheduled"));

    private static final Set<String> KNOWN_SOURCES = Set.of(
            "WEB", "WEBSITE", "REFERRAL", "EVENT", "COLD_CALL", "EMAIL", "SOCIAL", "PARTNER", "ADVERTISING");

    private LeadScoreFeatures() {
    }

    /** Must be called on the request thread: it reads the managed entity. */
    static JevState state(Lead lead, Instant now) {
        return JevState.builder(SCHEMA)
                .put("source", source(lead.getSource()))
                .put("priority", lead.getPriority())
                .put("status", lead.getStatus())
                .put("estimated_value_band", valueBand(lead.getEstimatedValue()))
                .put("currency", currency(lead.getCurrency()))
                .put("has_contact", lead.getContact() != null)
                .put("has_organization", lead.getOrganization() != null)
                .put("days_since_created", daysBetween(lead.getCreatedAt(), now))
                .put("days_since_last_contacted", daysBetween(lead.getLastContactedAt(), now))
                .put("has_next_action_date", lead.getNextActionDate() != null)
                .build();
    }

    static String source(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return KNOWN_SOURCES.contains(code) ? code : "OTHER";
    }

    static String valueBand(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            return "NONE";
        }
        if (value.compareTo(BigDecimal.valueOf(5_000)) < 0) {
            return "LT_5K";
        }
        if (value.compareTo(BigDecimal.valueOf(25_000)) < 0) {
            return "5K_25K";
        }
        if (value.compareTo(BigDecimal.valueOf(100_000)) < 0) {
            return "25K_100K";
        }
        return "GT_100K";
    }

    private static String currency(String raw) {
        return raw != null && raw.matches("[A-Za-z]{3}") ? raw.toUpperCase(Locale.ROOT) : null;
    }

    /** Null when the start is unknown, so "never contacted" is not confused with "contacted today". */
    private static Long daysBetween(Instant start, Instant now) {
        return start == null ? null : Math.max(0, Duration.between(start, now).toDays());
    }
}
