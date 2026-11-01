package com.opencrm.quote.ramp;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Expands an escalator into explicit absolute periods. Unit prices use scale 4,
 * half-up. PERCENT amount is percent points: 5 means 5 percent. AMOUNT adds
 * currency units. Steps do not store a discount fraction. Each period is exactly
 * {@code everyMonths} long. A term that length does not divide is
 * {@code RAMP_TERM_MISMATCH} and produces no steps.
 */
@Component
public class RampEscalatorExpander {

    static final int UNIT_SCALE = 4;

    public List<RampStepDto> expand(int durationInMonths, RampEscalatorDto escalator, Integer quantity) {
        return expand(durationInMonths, escalator, quantity, Integer.MAX_VALUE);
    }

    public List<RampStepDto> expand(int durationInMonths, RampEscalatorDto escalator, Integer quantity, int maxPeriods) {
        if (durationInMonths < 1) {
            throw RampScheduleException.invalid(List.of(RampReasons.TERM_MISMATCH));
        }
        if (escalator == null || escalator.getEveryMonths() == null || escalator.getEveryMonths() < 1) {
            throw RampScheduleException.invalid(List.of(RampReasons.PERIOD_EMPTY));
        }
        int everyMonths = escalator.getEveryMonths();
        if (durationInMonths % everyMonths != 0) {
            throw RampScheduleException.invalid(List.of(RampReasons.TERM_MISMATCH));
        }
        if (escalator.getMode() == null || escalator.getStartPrice() == null || escalator.getAmount() == null) {
            throw RampScheduleException.invalid(List.of(RampReasons.PERIOD_EMPTY));
        }
        if (escalator.getStartPrice().signum() < 0 || escalator.getAmount().signum() < 0) {
            throw RampScheduleException.invalid(List.of(RampReasons.NEGATIVE_VALUE));
        }

        int count = durationInMonths / everyMonths;
        if (count > maxPeriods) {
            throw RampScheduleException.invalid(List.of(RampReasons.TOO_MANY_PERIODS));
        }
        BigDecimal price = escalator.getStartPrice().setScale(UNIT_SCALE, RoundingMode.HALF_UP);
        List<RampStepDto> steps = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            steps.add(RampStepDto.builder()
                    .sequence(index + 1)
                    .startMonth(1 + (index * everyMonths))
                    .months(everyMonths)
                    .quantity(quantity)
                    .priceBasis(RampPriceBasis.ABSOLUTE)
                    .unitPrice(price)
                    .build());
            if (index + 1 < count) {
                price = nextPrice(price, escalator);
            }
        }
        return List.copyOf(steps);
    }

    /**
     * Appends periods for {@code durationInMonths} minus the months the existing
     * steps already cover. Existing step objects are returned unchanged. The first
     * new unit is one escalation of the last step's absolute unit price.
     */
    public List<RampStepDto> extend(List<RampStepDto> existing, int durationInMonths, RampEscalatorDto escalator) {
        return extend(existing, durationInMonths, escalator, Integer.MAX_VALUE);
    }

    public List<RampStepDto> extend(List<RampStepDto> existing, int durationInMonths, RampEscalatorDto escalator,
                                    int maxPeriods) {
        if (escalator == null || escalator.getEveryMonths() == null || escalator.getEveryMonths() < 1
                || escalator.getMode() == null || escalator.getAmount() == null) {
            throw RampScheduleException.invalid(List.of(RampReasons.PERIOD_EMPTY));
        }
        if (escalator.getAmount().signum() < 0
                || (escalator.getStartPrice() != null && escalator.getStartPrice().signum() < 0)) {
            throw RampScheduleException.invalid(List.of(RampReasons.NEGATIVE_VALUE));
        }
        if (existing == null || existing.isEmpty()) {
            throw RampScheduleException.invalid(List.of(RampReasons.PERIOD_EMPTY));
        }
        if (existing.size() > maxPeriods) {
            throw RampScheduleException.invalid(List.of(RampReasons.TOO_MANY_PERIODS));
        }

        List<RampStepDto> ordered = existing.stream()
                .sorted(Comparator.comparing((RampStepDto step) -> step == null || step.getStartMonth() == null
                        ? Integer.MAX_VALUE
                        : step.getStartMonth()))
                .toList();
        long expectedStart = 1;
        long coveredMonths = 0;
        for (RampStepDto step : ordered) {
            if (step == null) {
                throw RampScheduleException.invalid(List.of(RampReasons.PERIOD_EMPTY));
            }
            Integer months = step.getMonths();
            if (months == null || months < 1) {
                throw RampScheduleException.invalid(List.of(RampReasons.PERIOD_EMPTY));
            }
            Integer start = step.getStartMonth();
            if (start == null || start < 1 || start > expectedStart) {
                throw RampScheduleException.invalid(List.of(RampReasons.GAP));
            }
            if (start < expectedStart) {
                throw RampScheduleException.invalid(List.of(RampReasons.OVERLAP));
            }
            expectedStart = (long) start + months;
            coveredMonths += months;
            if (step.getUnitPrice() != null && step.getUnitPrice().signum() < 0) {
                throw RampScheduleException.invalid(List.of(RampReasons.NEGATIVE_VALUE));
            }
            if (step.getQuantity() != null && step.getQuantity() < 0) {
                throw RampScheduleException.invalid(List.of(RampReasons.NEGATIVE_VALUE));
            }
        }

        RampStepDto last = ordered.get(ordered.size() - 1);
        if (last.getPriceBasis() != RampPriceBasis.ABSOLUTE || last.getUnitPrice() == null) {
            throw RampScheduleException.invalid(List.of(RampReasons.PRICE_BASIS));
        }
        if (durationInMonths <= coveredMonths) {
            throw RampScheduleException.invalid(List.of(RampReasons.TERM_MISMATCH));
        }
        int everyMonths = escalator.getEveryMonths();
        long extension = durationInMonths - coveredMonths;
        if (extension % everyMonths != 0) {
            throw RampScheduleException.invalid(List.of(RampReasons.TERM_MISMATCH));
        }

        long countLong = extension / everyMonths;
        if (countLong + existing.size() > maxPeriods) {
            throw RampScheduleException.invalid(List.of(RampReasons.TOO_MANY_PERIODS));
        }
        int count = (int) countLong;
        int sequence = last.getSequence() == null ? ordered.size() : last.getSequence();
        BigDecimal price = last.getUnitPrice();
        List<RampStepDto> steps = new ArrayList<>(existing.size() + count);
        steps.addAll(existing);
        for (int index = 0; index < count; index++) {
            price = nextPrice(price, escalator);
            sequence++;
            steps.add(RampStepDto.builder()
                    .sequence(sequence)
                    .startMonth(Math.toIntExact(coveredMonths + 1 + ((long) index * everyMonths)))
                    .months(everyMonths)
                    .quantity(last.getQuantity())
                    .priceBasis(RampPriceBasis.ABSOLUTE)
                    .unitPrice(price)
                    .build());
        }
        return List.copyOf(steps);
    }

    private static BigDecimal nextPrice(BigDecimal price, RampEscalatorDto escalator) {
        if (escalator.getMode() == RampEscalatorMode.PERCENT) {
            BigDecimal factor = BigDecimal.ONE.add(escalator.getAmount().movePointLeft(2));
            return price.multiply(factor).setScale(UNIT_SCALE, RoundingMode.HALF_UP);
        }
        return price.add(escalator.getAmount()).setScale(UNIT_SCALE, RoundingMode.HALF_UP);
    }
}
