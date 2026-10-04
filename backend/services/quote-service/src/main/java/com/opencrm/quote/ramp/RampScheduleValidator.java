package com.opencrm.quote.ramp;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Write-time checks for a ramp schedule. The first failing rule returns 422
 * {@code RAMP_SCHEDULE_INVALID} with one reason token. This class does not call
 * pricing and does not share a validator with libs/common.
 */
@Component
public class RampScheduleValidator {

    static final Set<String> ALLOWED_BILLING = Set.of(
            "MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "BIENNIAL");

    static final Set<String> REJECTED_LINE_TYPES = Set.of("DISCOUNT", "FEE", "TAX");

    public void validate(
            List<RampStepDto> steps,
            int durationInMonths,
            String billingFrequency,
            String lineItemType,
            int maxPeriods) {
        if (!lineAllowsSchedule(billingFrequency, lineItemType)) {
            fail(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE);
        }
        if (steps == null || steps.isEmpty()) {
            fail(RampReasons.PERIOD_EMPTY);
        }
        if (steps.size() > maxPeriods) {
            fail(RampReasons.TOO_MANY_PERIODS);
        }

        List<RampStepDto> ordered = steps.stream()
                .sorted(Comparator
                        .comparing((RampStepDto step) -> step == null || step.getStartMonth() == null
                                ? Integer.MAX_VALUE
                                : step.getStartMonth())
                        .thenComparing(step -> step == null || step.getSequence() == null
                                ? Integer.MAX_VALUE
                                : step.getSequence()))
                .toList();

        int expectedStart = 1;
        int coveredMonths = 0;
        for (RampStepDto step : ordered) {
            if (step == null) {
                fail(RampReasons.PERIOD_EMPTY);
            }
            Integer months = step.getMonths();
            if (months == null || months < 1) {
                fail(RampReasons.PERIOD_EMPTY);
            }
            Integer start = step.getStartMonth();
            if (start == null || start < 1 || start > expectedStart) {
                fail(RampReasons.GAP);
            }
            if (start < expectedStart) {
                fail(RampReasons.OVERLAP);
            }
            expectedStart = start + months;
            coveredMonths += months;
            rejectPriceAndQuantity(step);
        }
        if (durationInMonths < 1 || coveredMonths != durationInMonths) {
            fail(RampReasons.TERM_MISMATCH);
        }
    }

    static boolean lineAllowsSchedule(String billingFrequency, String lineItemType) {
        String billing = normalize(billingFrequency);
        if (billing == null || !ALLOWED_BILLING.contains(billing)) {
            return false;
        }
        String type = normalize(lineItemType);
        return type == null || !REJECTED_LINE_TYPES.contains(type);
    }

    /**
     * Price basis is checked before sign. A discount fraction above 1 is percent
     * points stored in a fraction field, so it is {@code RAMP_PRICE_BASIS}.
     * A missing quantity has no token of its own. The column is NOT NULL, so the
     * step is refused as an incomplete period and nothing is persisted.
     */
    private static void rejectPriceAndQuantity(RampStepDto step) {
        if (step.getPriceBasis() == RampPriceBasis.ABSOLUTE) {
            if (step.getUnitPrice() == null || step.getDiscountFraction() != null) {
                fail(RampReasons.PRICE_BASIS);
            } else if (step.getUnitPrice().signum() < 0) {
                fail(RampReasons.NEGATIVE_VALUE);
            }
        } else if (step.getPriceBasis() == RampPriceBasis.DISCOUNT_FRACTION) {
            BigDecimal discount = step.getDiscountFraction();
            if (discount == null || step.getUnitPrice() != null || discount.compareTo(BigDecimal.ONE) > 0) {
                fail(RampReasons.PRICE_BASIS);
            } else if (discount.signum() < 0) {
                fail(RampReasons.NEGATIVE_VALUE);
            }
        } else {
            fail(RampReasons.PRICE_BASIS);
        }
        Integer quantity = step.getQuantity();
        if (quantity != null && quantity < 0) {
            fail(RampReasons.NEGATIVE_VALUE);
        }
        if (quantity == null) {
            fail(RampReasons.PERIOD_EMPTY);
        }
    }

    private static void fail(String reason) {
        throw RampScheduleException.invalid(List.of(reason));
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
