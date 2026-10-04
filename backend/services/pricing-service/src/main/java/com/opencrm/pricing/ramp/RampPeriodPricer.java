package com.opencrm.pricing.ramp;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.pricing.RampPriceBasis;
import com.opencrm.common.pricing.RampStep;
import com.opencrm.pricing.waterfall.PricingLineRequest;
import com.opencrm.pricing.waterfall.PricingWaterfallStep;
import com.opencrm.pricing.waterfall.WaterfallStepType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Prices one ramp line from an already resolved list price. Named-seat tiers apply
 * only here, using that period's quantity (ADR-023 D-07).
 */
@Component
public class RampPeriodPricer {

    static final String NAMED_SEAT_LINE = "SAAS_SEAT_NAMED";
    static final BigDecimal TIER_100 = new BigDecimal("0.07");
    static final BigDecimal TIER_500 = new BigDecimal("0.12");
    static final BigDecimal TIER_1000 = new BigDecimal("0.18");

    private static final int UNIT_SCALE = 4;
    private static final int MONEY_SCALE = 2;
    private static final int RATE_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    public List<PricedPeriod> price(PricingLineRequest line, BigDecimal listPrice, int durationInMonths, int maxPeriods) {
        List<RampStep> steps = line.getSchedule();
        if (steps == null || steps.isEmpty()) {
            throw invalid("RAMP_PERIOD_EMPTY");
        }
        if (steps.size() > maxPeriods) {
            throw invalid("RAMP_TOO_MANY_PERIODS");
        }
        if (listPrice == null) {
            throw invalid("RAMP_PRICE_BASIS");
        }

        List<RampStep> ordered = steps.stream()
                .sorted(Comparator.comparing(step -> step == null || step.getStartMonth() == null
                        ? Integer.MAX_VALUE
                        : step.getStartMonth()))
                .toList();

        int expectedStart = 1;
        List<PricedPeriod> priced = new ArrayList<>();
        for (RampStep step : ordered) {
            if (step == null || step.getMonths() == null || step.getMonths() < 1) {
                throw invalid("RAMP_PERIOD_EMPTY");
            }
            Integer start = step.getStartMonth();
            if (start == null || start != expectedStart) {
                throw invalid(start != null && start < expectedStart ? "RAMP_OVERLAP" : "RAMP_GAP");
            }
            if (step.getQuantity() == null || step.getQuantity() < 0) {
                throw invalid(step.getQuantity() == null ? "RAMP_PERIOD_EMPTY" : "RAMP_NEGATIVE_VALUE");
            }
            expectedStart += step.getMonths();
            priced.add(priceStep(line.getLineItemType(), listPrice, step));
        }
        if (expectedStart - 1 != durationInMonths) {
            throw invalid("RAMP_TERM_MISMATCH");
        }
        return priced;
    }

    /**
     * Named-seat fraction copied from {@code volume-discount-named-seat.drl}. Zero for every other line type.
     */
    static BigDecimal namedSeatFraction(String lineItemType, int quantity) {
        if (!NAMED_SEAT_LINE.equals(lineItemType)) {
            return BigDecimal.ZERO;
        }
        if (quantity >= 1000) {
            return TIER_1000;
        }
        if (quantity >= 500) {
            return TIER_500;
        }
        if (quantity >= 100) {
            return TIER_100;
        }
        return BigDecimal.ZERO;
    }

    private static PricedPeriod priceStep(String lineItemType, BigDecimal listPrice, RampStep step) {
        BigDecimal base = baseUnit(listPrice, step);
        BigDecimal tier = namedSeatFraction(lineItemType, step.getQuantity());
        BigDecimal netUnit = base.multiply(BigDecimal.ONE.subtract(tier)).setScale(UNIT_SCALE, ROUNDING);
        BigDecimal netMonthly = netUnit.multiply(BigDecimal.valueOf(step.getQuantity())).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal periodAmount = netMonthly.multiply(BigDecimal.valueOf(step.getMonths())).setScale(MONEY_SCALE, ROUNDING);
        BigDecimal discountRate = listPrice.signum() == 0
                ? BigDecimal.ZERO.setScale(RATE_SCALE, ROUNDING)
                : listPrice.subtract(netUnit).divide(listPrice, RATE_SCALE, ROUNDING);

        List<String> codes = new ArrayList<>();
        if (tier.signum() > 0) {
            codes.add("NAMED_SEAT");
        }
        if (step.getPriceBasis() == RampPriceBasis.ABSOLUTE) {
            codes.add("ABSOLUTE_PRICE");
        }

        List<PricingWaterfallStep> waterfall = new ArrayList<>();
        waterfall.add(step(WaterfallStepType.PERIOD_SELECTION,
                "Period " + step.getSequence() + " month " + step.getStartMonth() + " for " + step.getMonths()
                        + " months, quantity " + step.getQuantity(),
                periodAmount,
                periodAmount));
        waterfall.add(step(WaterfallStepType.LIST_PRICE, "List price", listPrice, listPrice));
        waterfall.add(step(WaterfallStepType.BASE_PRICE, "Base price", base, base));
        if (tier.signum() > 0 || discountRate.signum() > 0) {
            waterfall.add(step(WaterfallStepType.DISCOUNT, "Discount", listPrice.subtract(netUnit), netUnit));
        }
        waterfall.add(step(WaterfallStepType.NET_PRICE, "Net unit price", netUnit, netMonthly));

        return PricedPeriod.builder()
                .sequence(step.getSequence())
                .startMonth(step.getStartMonth())
                .months(step.getMonths())
                .quantity(step.getQuantity())
                .netUnitPrice(netUnit)
                .netMonthly(netMonthly)
                .periodAmount(periodAmount)
                .discountRate(discountRate)
                .explanationCodes(codes)
                .waterfall(waterfall)
                .build();
    }

    private static BigDecimal baseUnit(BigDecimal listPrice, RampStep step) {
        if (step.getPriceBasis() == RampPriceBasis.ABSOLUTE) {
            if (step.getUnitPrice() == null || step.getDiscountFraction() != null || step.getUnitPrice().signum() < 0) {
                throw invalid("RAMP_PRICE_BASIS");
            }
            return step.getUnitPrice();
        }
        if (step.getPriceBasis() == RampPriceBasis.DISCOUNT_FRACTION) {
            BigDecimal fraction = step.getDiscountFraction();
            if (fraction == null || step.getUnitPrice() != null
                    || fraction.signum() < 0 || fraction.compareTo(BigDecimal.ONE) > 0) {
                throw invalid("RAMP_PRICE_BASIS");
            }
            return listPrice.multiply(BigDecimal.ONE.subtract(fraction));
        }
        throw invalid("RAMP_PRICE_BASIS");
    }

    private static PricingWaterfallStep step(WaterfallStepType type, String description, BigDecimal amount, BigDecimal running) {
        return PricingWaterfallStep.builder()
                .stepType(type)
                .description(description)
                .amount(amount)
                .runningTotal(running)
                .build();
    }

    private static BusinessException invalid(String reason) {
        return new BusinessException(reason, HttpStatus.UNPROCESSABLE_CONTENT, "RAMP_SCHEDULE_INVALID");
    }
}
