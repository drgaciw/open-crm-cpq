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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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

    /**
     * Lines that may carry a schedule. Same sets as quote-service's write-time
     * {@code RampScheduleValidator}; ADR-011 keeps the two checks separate and the
     * golden fixtures keep them aligned (ADR-023, "Validation lives twice").
     */
    static final Set<String> ALLOWED_BILLING = Set.of(
            "MONTHLY", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "BIENNIAL");
    static final Set<String> REJECTED_LINE_TYPES = Set.of("DISCOUNT", "FEE", "TAX");

    public List<PricedPeriod> price(PricingLineRequest line, BigDecimal listPrice, int durationInMonths, int maxPeriods) {
        return price(line, listPrice, durationInMonths, maxPeriods, null);
    }

    public List<PricedPeriod> price(
            PricingLineRequest line,
            BigDecimal listPrice,
            int durationInMonths,
            int maxPeriods,
            LocalDate anchorDate) {
        if (!lineAllowsSchedule(line.getBillingFrequency(), line.getLineItemType())) {
            throw invalid("RAMP_NOT_ALLOWED_FOR_LINE_TYPE");
        }
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
            LocalDate effectiveDate = periodEffectiveDate(anchorDate, step.getStartMonth());
            priced.add(priceStep(line.getLineItemType(), listPrice, step, effectiveDate));
        }
        if (expectedStart - 1 != durationInMonths) {
            throw invalid("RAMP_TERM_MISMATCH");
        }
        return priced;
    }

    /**
     * ADR-023 D-04: each period start is anchored to the contract term start (or pricing-date fallback),
     * never chained from the previous period boundary.
     */
    static LocalDate periodEffectiveDate(LocalDate anchorDate, int startMonth) {
        return anchorDate == null ? null : anchorDate.plusMonths(startMonth - 1L);
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

    private static PricedPeriod priceStep(
            String lineItemType,
            BigDecimal listPrice,
            RampStep step,
            LocalDate effectiveDate) {
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
                .effectiveDate(effectiveDate)
                .quantity(step.getQuantity())
                .netUnitPrice(netUnit)
                .netMonthly(netMonthly)
                .periodAmount(periodAmount)
                .discountRate(discountRate)
                .explanationCodes(codes)
                .waterfall(waterfall)
                .build();
    }

    /**
     * A schedule needs a recurring billing frequency and a line type that is not a
     * discount, fee or tax. A missing frequency is refused: one-time and perpetual
     * lines cannot be told apart from it otherwise.
     */
    static boolean lineAllowsSchedule(String billingFrequency, String lineItemType) {
        String billing = normalize(billingFrequency);
        if (billing == null || !ALLOWED_BILLING.contains(billing)) {
            return false;
        }
        String type = normalize(lineItemType);
        return type == null || !REJECTED_LINE_TYPES.contains(type);
    }

    /**
     * Price basis is checked before sign, as in quote-service: a wrong field set or a
     * fraction above 1 is {@code RAMP_PRICE_BASIS}; a negative price or fraction is
     * {@code RAMP_NEGATIVE_VALUE}.
     */
    private static BigDecimal baseUnit(BigDecimal listPrice, RampStep step) {
        if (step.getPriceBasis() == RampPriceBasis.ABSOLUTE) {
            if (step.getUnitPrice() == null || step.getDiscountFraction() != null) {
                throw invalid("RAMP_PRICE_BASIS");
            }
            if (step.getUnitPrice().signum() < 0) {
                throw invalid("RAMP_NEGATIVE_VALUE");
            }
            return step.getUnitPrice();
        }
        if (step.getPriceBasis() == RampPriceBasis.DISCOUNT_FRACTION) {
            BigDecimal fraction = step.getDiscountFraction();
            if (fraction == null || step.getUnitPrice() != null || fraction.compareTo(BigDecimal.ONE) > 0) {
                throw invalid("RAMP_PRICE_BASIS");
            }
            if (fraction.signum() < 0) {
                throw invalid("RAMP_NEGATIVE_VALUE");
            }
            return listPrice.multiply(BigDecimal.ONE.subtract(fraction));
        }
        throw invalid("RAMP_PRICE_BASIS");
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT);
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
