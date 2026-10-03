package com.opencrm.quote.ramp;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
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

    private static BigDecimal nextPrice(BigDecimal price, RampEscalatorDto escalator) {
        if (escalator.getMode() == RampEscalatorMode.PERCENT) {
            BigDecimal factor = BigDecimal.ONE.add(escalator.getAmount().movePointLeft(2));
            return price.multiply(factor).setScale(UNIT_SCALE, RoundingMode.HALF_UP);
        }
        return price.add(escalator.getAmount()).setScale(UNIT_SCALE, RoundingMode.HALF_UP);
    }
}
