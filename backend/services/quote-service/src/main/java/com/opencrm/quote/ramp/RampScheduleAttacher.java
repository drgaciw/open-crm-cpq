package com.opencrm.quote.ramp;

import com.opencrm.quote.lineitem.LineItemCreateRequest;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a schedule before the line is saved, then replaces child rows.
 * Flag off throws before any mutation. Explicit steps win over the escalator.
 * Escalator inputs are stored for display only.
 */
@Component
@RequiredArgsConstructor
public class RampScheduleAttacher {

    private final RampPricingProperties properties;
    private final RampScheduleValidator validator;
    private final RampEscalatorExpander expander;

    public PreparedRampSchedule prepare(Quote quote, LineItemCreateRequest request) {
        RampScheduleDto schedule = request.getSchedule();
        if (!isAuthoring(schedule)) {
            return null;
        }
        if (!properties.isEnabled()) {
            throw RampScheduleException.notSupported();
        }
        boolean explicitSteps = schedule.getSteps() != null && !schedule.getSteps().isEmpty();
        List<RampStepDto> steps = explicitSteps
                ? schedule.getSteps()
                : expander.expand(quote.getDurationInMonths(), schedule.getEscalator(), request.getQuantity());
        validator.validate(
                steps,
                quote.getDurationInMonths(),
                request.getBillingFrequency(),
                request.getLineItemType(),
                properties.getMaxPeriods());
        return new PreparedRampSchedule(List.copyOf(steps), schedule.getEscalator());
    }

    public void attach(QuoteLineItem item, PreparedRampSchedule prepared) {
        if (item.getRampSteps() == null) {
            item.setRampSteps(new ArrayList<>());
        }
        item.getRampSteps().clear();
        if (prepared == null) {
            clearEscalator(item);
            return;
        }
        for (RampStepDto step : prepared.steps()) {
            QuoteRampStep row = toRow(step);
            row.setLineItem(item);
            item.getRampSteps().add(row);
        }
        applyEscalator(item, prepared.escalator());
    }

    public static boolean isAuthoring(RampScheduleDto schedule) {
        if (schedule == null) {
            return false;
        }
        boolean hasSteps = schedule.getSteps() != null && !schedule.getSteps().isEmpty();
        return hasSteps || schedule.getEscalator() != null;
    }

    private static QuoteRampStep toRow(RampStepDto step) {
        return QuoteRampStep.builder()
                .sequence(step.getSequence())
                .startMonth(step.getStartMonth())
                .months(step.getMonths())
                .quantity(step.getQuantity())
                .priceBasis(step.getPriceBasis().name())
                .unitPrice(scale4(step.getUnitPrice()))
                .discountFraction(scale4(step.getDiscountFraction()))
                .build();
    }

    private static void applyEscalator(QuoteLineItem item, RampEscalatorDto escalator) {
        if (escalator == null) {
            clearEscalator(item);
            return;
        }
        item.setRampEscalatorStartPrice(scale4(escalator.getStartPrice()));
        item.setRampEscalatorMode(escalator.getMode() == null ? null : escalator.getMode().name());
        item.setRampEscalatorAmount(scale4(escalator.getAmount()));
        item.setRampEscalatorEveryMonths(escalator.getEveryMonths());
    }

    private static void clearEscalator(QuoteLineItem item) {
        item.setRampEscalatorStartPrice(null);
        item.setRampEscalatorMode(null);
        item.setRampEscalatorAmount(null);
        item.setRampEscalatorEveryMonths(null);
    }

    private static BigDecimal scale4(BigDecimal value) {
        return value == null ? null : value.setScale(RampEscalatorExpander.UNIT_SCALE, RoundingMode.HALF_UP);
    }

    public record PreparedRampSchedule(List<RampStepDto> steps, RampEscalatorDto escalator) {
    }
}
