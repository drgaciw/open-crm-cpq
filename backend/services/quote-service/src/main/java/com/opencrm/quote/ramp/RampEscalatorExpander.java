package com.opencrm.quote.ramp;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Expands an escalator into explicit periods. Unit prices use scale 4, half-up.
 * PERCENT amount is the percent number: 5 means 5 percent, compounded each period.
 * AMOUNT adds the amount at each anniversary. The caller stores the steps, not a formula.
 */
@Component
public class RampEscalatorExpander {

    static final int UNIT_SCALE = 4;

    public List<RampStepDto> expand(int durationInMonths, RampEscalatorDto escalator, Integer quantity) {
        if (durationInMonths < 1) {
            throw RampScheduleException.invalid(List.of(RampReasons.TERM_MISMATCH));
        }
        if (escalator == null
                || escalator.getMode() == null
                || escalator.getEveryMonths() == null
                || escalator.getEveryMonths() < 1
                || escalator.getStartPrice() == null
                || escalator.getAmount() == null) {
            throw RampScheduleException.invalid(List.of(RampReasons.ESCALATOR_INVALID));
        }
        if (escalator.getStartPrice().signum() < 0 || escalator.getAmount().signum() < 0) {
            throw RampScheduleException.invalid(List.of(RampReasons.NEGATIVE_VALUE));
        }

        BigDecimal price = escalator.getStartPrice().setScale(UNIT_SCALE, RoundingMode.HALF_UP);
        int remaining = durationInMonths;
        int startMonth = 1;
        int sequence = 1;
        List<RampStepDto> steps = new ArrayList<>();
        while (remaining > 0) {
            int months = Math.min(escalator.getEveryMonths(), remaining);
            steps.add(RampStepDto.builder()
                    .sequence(sequence)
                    .startMonth(startMonth)
                    .months(months)
                    .quantity(quantity)
                    .priceBasis(RampPriceBasis.ABSOLUTE)
                    .unitPrice(price)
                    .build());
            remaining -= months;
            startMonth += months;
            sequence++;
            price = nextPrice(price, escalator);
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
