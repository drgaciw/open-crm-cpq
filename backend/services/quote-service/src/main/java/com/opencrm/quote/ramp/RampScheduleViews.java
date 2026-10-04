package com.opencrm.quote.ramp;

import com.opencrm.quote.lineitem.QuoteLineItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps persisted steps onto the quote-service DTO. Returns null when the line has no schedule.
 */
public final class RampScheduleViews {

    private RampScheduleViews() {
    }

    public static boolean hasSteps(QuoteLineItem item) {
        return !activeSteps(item).isEmpty();
    }

    public static List<QuoteRampStep> activeSteps(QuoteLineItem item) {
        if (item == null || item.getRampSteps() == null) {
            return List.of();
        }
        return item.getRampSteps().stream()
                .filter(step -> step != null && !step.isDeleted())
                .sorted(Comparator.comparingInt(QuoteRampStep::getSequence))
                .toList();
    }

    public static List<RampStepDto> stepDtos(QuoteLineItem item) {
        List<RampStepDto> steps = new ArrayList<>();
        for (QuoteRampStep step : activeSteps(item)) {
            steps.add(toDto(step));
        }
        return steps;
    }

    public static RampScheduleDto toDto(QuoteLineItem item) {
        if (item == null) {
            return null;
        }
        List<RampStepDto> steps = stepDtos(item);
        RampEscalatorDto escalator = escalatorOf(item);
        if (steps.isEmpty() && escalator == null) {
            return null;
        }
        return RampScheduleDto.builder()
                .steps(steps.isEmpty() ? null : steps)
                .escalator(escalator)
                .build();
    }

    public static RampStepDto toDto(QuoteRampStep step) {
        RampPriceBasis basis = step.getPriceBasis() == null ? null : RampPriceBasis.valueOf(step.getPriceBasis());
        return RampStepDto.builder()
                .sequence(step.getSequence())
                .startMonth(step.getStartMonth())
                .months(step.getMonths())
                .quantity(step.getQuantity())
                .priceBasis(basis)
                .unitPrice(step.getUnitPrice())
                .discountFraction(step.getDiscountFraction())
                .build();
    }

    private static RampEscalatorDto escalatorOf(QuoteLineItem item) {
        if (item.getRampEscalatorMode() == null) {
            return null;
        }
        return RampEscalatorDto.builder()
                .startPrice(item.getRampEscalatorStartPrice())
                .mode(RampEscalatorMode.valueOf(item.getRampEscalatorMode()))
                .amount(item.getRampEscalatorAmount())
                .everyMonths(item.getRampEscalatorEveryMonths())
                .build();
    }
}
