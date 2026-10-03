package com.opencrm.quote.ramp;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Write-time checks for FR-02. Pricing-service repeats them later. This class
 * does not call pricing and does not share a validator with libs/common.
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
            throw RampScheduleException.invalid(List.of(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE));
        }
        if (steps == null || steps.isEmpty()) {
            throw RampScheduleException.invalid(List.of(RampReasons.PERIOD_EMPTY));
        }

        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (steps.size() > maxPeriods) {
            reasons.add(RampReasons.TOO_MANY_PERIODS);
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
        Set<Integer> sequences = new HashSet<>();
        boolean pricesWellFormed = true;
        boolean anyBillable = false;

        for (RampStepDto step : ordered) {
            if (step == null) {
                reasons.add(RampReasons.PERIOD_EMPTY);
                pricesWellFormed = false;
                continue;
            }
            if (step.getSequence() == null || step.getSequence() < 1 || !sequences.add(step.getSequence())) {
                reasons.add(RampReasons.SEQUENCE);
            }
            Integer months = step.getMonths();
            if (months == null || months < 1) {
                reasons.add(RampReasons.PERIOD_EMPTY);
            }
            Integer start = step.getStartMonth();
            if (start == null || start < 1) {
                reasons.add(RampReasons.GAP);
            } else if (months != null && months >= 1) {
                if (start > expectedStart) {
                    reasons.add(RampReasons.GAP);
                }
                if (start < expectedStart) {
                    reasons.add(RampReasons.OVERLAP);
                }
                expectedStart = start + months;
                coveredMonths += months;
            }
            if (!priceWellFormed(step, reasons)) {
                pricesWellFormed = false;
            } else if (billable(step)) {
                anyBillable = true;
            }
        }

        if (durationInMonths < 1 || coveredMonths != durationInMonths) {
            reasons.add(RampReasons.TERM_MISMATCH);
        }
        if (pricesWellFormed && !anyBillable) {
            reasons.add(RampReasons.NOT_BILLABLE);
        }
        if (!reasons.isEmpty()) {
            throw RampScheduleException.invalid(List.copyOf(reasons));
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

    private static boolean priceWellFormed(RampStepDto step, Set<String> reasons) {
        boolean wellFormed = true;
        if (step.getQuantity() == null) {
            reasons.add(RampReasons.QUANTITY_REQUIRED);
            wellFormed = false;
        } else if (step.getQuantity() < 0) {
            reasons.add(RampReasons.NEGATIVE_VALUE);
            wellFormed = false;
        }
        if (step.getPriceBasis() == RampPriceBasis.ABSOLUTE) {
            if (step.getUnitPrice() == null || step.getDiscountFraction() != null) {
                reasons.add(RampReasons.PRICE_BASIS);
                wellFormed = false;
            } else if (step.getUnitPrice().signum() < 0) {
                reasons.add(RampReasons.NEGATIVE_VALUE);
                wellFormed = false;
            }
        } else if (step.getPriceBasis() == RampPriceBasis.DISCOUNT_FRACTION) {
            if (step.getDiscountFraction() == null || step.getUnitPrice() != null) {
                reasons.add(RampReasons.PRICE_BASIS);
                wellFormed = false;
            } else if (step.getDiscountFraction().signum() < 0) {
                reasons.add(RampReasons.NEGATIVE_VALUE);
                wellFormed = false;
            } else if (step.getDiscountFraction().compareTo(BigDecimal.ONE) > 0) {
                reasons.add(RampReasons.PRICE_BASIS);
                wellFormed = false;
            }
        } else {
            reasons.add(RampReasons.PRICE_BASIS);
            wellFormed = false;
        }
        return wellFormed;
    }

    /**
     * A period is billable when it can produce a positive charge: quantity above zero,
     * and either a positive absolute unit price or a discount fraction strictly below 1.
     */
    private static boolean billable(RampStepDto step) {
        if (step.getQuantity() == null || step.getQuantity() <= 0) {
            return false;
        }
        if (step.getPriceBasis() == RampPriceBasis.ABSOLUTE) {
            return step.getUnitPrice() != null && step.getUnitPrice().signum() > 0;
        }
        if (step.getPriceBasis() == RampPriceBasis.DISCOUNT_FRACTION) {
            return step.getDiscountFraction() != null
                    && step.getDiscountFraction().signum() >= 0
                    && step.getDiscountFraction().compareTo(BigDecimal.ONE) < 0;
        }
        return false;
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
