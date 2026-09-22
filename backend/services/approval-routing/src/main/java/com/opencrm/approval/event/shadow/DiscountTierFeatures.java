package com.opencrm.approval.event.shadow;

import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.question.JevQuestions;
import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.state.JevState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the discount-tier shadow may send to Jev (ADR-015). Quote and line identifiers are deliberately
 * absent: the tier depends on the numbers, and identifiers would only add linkable data to the payload.
 */
final class DiscountTierFeatures {

    static final JevFeatureSchema SCHEMA = JevFeatureSchema.named("discount-tier", 1)
            .number("margin_delta")
            .number("requested_discount")
            .code("currency_code")
            .build();

    static final String QUESTION_ID = "tier";

    /**
     * Rubric {@code tier-v1-thresholds}: restates the authoritative rule, so agreement measures fidelity
     * rather than added judgement. A later intent-based rubric is the informative comparison.
     */
    static final JevQuestion.Choice QUESTION = JevQuestions.choice(QUESTION_ID,
            "Which approval tier should review this discount, given how far it moves margin?", criteria());

    private DiscountTierFeatures() {
    }

    static JevState state(DiscountApprovalRequiredEvent event) {
        return JevState.builder(SCHEMA)
                .put("margin_delta", event.getMarginDelta())
                .put("requested_discount", event.getRequestedDiscount())
                .put("currency_code", event.getCurrencyCode())
                .build();
    }

    private static Map<String, String> criteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put("MANAGER", "Margin delta of 20 or less");
        criteria.put("DIRECTOR", "Margin delta above 20 and up to 30");
        criteria.put("VP_SALES", "Margin delta above 30");
        return criteria;
    }
}
