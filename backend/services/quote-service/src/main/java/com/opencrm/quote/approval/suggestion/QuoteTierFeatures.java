package com.opencrm.quote.approval.suggestion;

import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.question.JevQuestions;
import com.opencrm.jev.state.JevFeatureSchema;
import com.opencrm.jev.state.JevState;
import com.opencrm.quote.approval.ApprovalTier;
import com.opencrm.quote.quote.Quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What the quote-tier suggestion may send to Jev (ADR-015): deal shape only. Quote name, number,
 * description, notes, metadata, customer and deal identifiers, and the pricing waterfall stay in the
 * service. Amounts are banded, never exact.
 */
final class QuoteTierFeatures {

    static final JevFeatureSchema SCHEMA = JevFeatureSchema.named("quote-tier", 1)
            .code("total_amount_band")
            .number("discount_pct")
            .integer("duration_months")
            .code("deal_type")
            .integer("line_count")
            .code("currency")
            .code("region")
            .build();

    static final String QUESTION_ID = "tier";

    /** Rubric {@code quote-tier-v1}. Keys are exactly the {@link ApprovalTier} names. */
    static final JevQuestion.Choice QUESTION = JevQuestions.choice(QUESTION_ID,
            "Which approval tier should review this quote, given its size, discount and term?", criteria());

    private QuoteTierFeatures() {
    }

    /** Must be called inside a transaction: it counts the lazily loaded line items. */
    static JevState state(Quote quote) {
        return JevState.builder(SCHEMA)
                .put("total_amount_band", amountBand(quote.getTotalAmount()))
                .put("discount_pct", discountPct(quote.getSubtotal(), quote.getTotalDiscount()))
                .put("duration_months", quote.getDurationInMonths())
                .put("deal_type", quote.getDealType())
                .put("line_count", quote.getLineItems() == null ? 0 : quote.getLineItems().size())
                .put("currency", code(quote.getCurrency(), 3, 3))
                .put("region", code(quote.getRegion(), 2, 8))
                .build();
    }

    static String amountBand(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.valueOf(10_000)) < 0) {
            return "LT_10K";
        }
        if (amount.compareTo(BigDecimal.valueOf(50_000)) < 0) {
            return "10K_50K";
        }
        if (amount.compareTo(BigDecimal.valueOf(250_000)) < 0) {
            return "50K_250K";
        }
        if (amount.compareTo(BigDecimal.valueOf(1_000_000)) < 0) {
            return "250K_1M";
        }
        return "GT_1M";
    }

    /** Discount as a percentage of subtotal, one decimal; 0 when there is nothing to discount. */
    static double discountPct(BigDecimal subtotal, BigDecimal discount) {
        if (subtotal == null || discount == null || subtotal.signum() <= 0) {
            return 0;
        }
        return discount.multiply(BigDecimal.valueOf(100))
                .divide(subtotal, 1, RoundingMode.HALF_UP)
                .max(BigDecimal.ZERO)
                .doubleValue();
    }

    /**
     * {@code region} and {@code currency} are free-text columns. Only a short all-letters code is passed
     * through; anything else becomes OTHER so typed text can never reach the payload.
     */
    static String code(String raw, int minLength, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        return value.matches("[A-Z]{" + minLength + "," + maxLength + "}") ? value : "OTHER";
    }

    private static Map<String, String> criteria() {
        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put(ApprovalTier.DEAL_DESK.name(), "Routine quote: modest size and a standard discount");
        criteria.put(ApprovalTier.SALES_VP.name(), "Large quote or a discount beyond standard limits");
        criteria.put(ApprovalTier.CFO.name(), "Very large quote, or a discount that materially affects margin");
        return criteria;
    }
}
