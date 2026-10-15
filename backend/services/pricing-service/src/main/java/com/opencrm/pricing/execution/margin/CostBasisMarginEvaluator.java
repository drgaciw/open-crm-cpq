package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.cost.CostBasisStaleness;
import com.opencrm.pricing.execution.MetricsCalculator;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Derives cost-basis margin (finance decision F-1) for every line and every ramp period of a priced quote.
 *
 * <p>Margin is {@code (net − cost) / net}, a fraction. A flat line's net is its final price and its cost is unit
 * cost × quantity. A ramp period's net is its {@link PricedPeriod#getPeriodAmount() period amount}, which a hybrid
 * migration credit lowers, and its cost is unit cost × quantity × months. The quote margin is weighted by the same
 * amounts as TCV. There is no margin, never a substitute, when the lookup is empty (no row, or a stale one), when
 * the cost is in a currency other than the price's (pricing-service has no conversion), or when the net is negative.
 * A net of exactly zero is margin-not-applicable ({@link #NOT_APPLICABLE_ZERO_NET}): it does not route, and a flat
 * line with zero net is left out of the quote margin.
 *
 * <p><b>Cost basis dates (port contract).</b> Each lookup passes two dates: {@code effectiveOn} selects the cost row
 * and is the period's start date (the pricing date for a flat line); {@code pricedOn} judges freshness and is always
 * the pricing date. A three-year ramp priced on a fresh cost basis therefore never fails on a future period start.
 *
 * <p><b>Interim: period start dates count from the pricing date.</b> Period {@code n} starting in contract month
 * {@code m} is dated pricing date + {@code (m − 1)} months, because no contract or term start date reaches the
 * pricing context ({@code PricingContext} and {@code PricingLineRequest} carry none; quote-service holds
 * {@code Quote.startDate} but does not send it). When the pricing request carries a term start, period starts
 * should count from it instead.
 *
 * <p><b>Replay.</b> An earlier assessment of identical input is reused, without calling the lookup, only when every
 * line and period in it had a usable cost (no missing or currency reason) and every recorded cost is still fresh on
 * the current pricing date. It then returns the cost and margin the quote was priced with, even if a newer version
 * has since landed on the same effective date. Otherwise the input is priced on the current cost basis, so a missing
 * or stale cost is never pinned.
 */
@Component
public class CostBasisMarginEvaluator {

    /** The lookup was empty: no cost row, or the newest row is stale. */
    public static final String COST_MISSING = "MARGIN_COST_MISSING";
    /** The cost's currency differs from the price's and pricing-service cannot convert it. */
    public static final String COST_CURRENCY_MISMATCH = "MARGIN_COST_CURRENCY_MISMATCH";
    /** The net is negative (or absent), so a margin fraction is undefined. Routes to Deal Desk. */
    public static final String NET_NOT_POSITIVE = "MARGIN_NET_NOT_POSITIVE";
    /** The net is exactly zero: margin does not apply. Not routed, and a zero-net line is out of the quote margin. */
    public static final String NOT_APPLICABLE_ZERO_NET = "MARGIN_NOT_APPLICABLE_ZERO_NET";

    private final CostBasisLookup lookup;
    private final Clock clock;

    @Autowired
    public CostBasisMarginEvaluator(CostBasisLookup lookup) {
        this(lookup, Clock.systemUTC());
    }

    public CostBasisMarginEvaluator(CostBasisLookup lookup, Clock clock) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * @param replayOf an earlier assessment of identical input, or {@code null}. It is used only when
     *                 {@link #replayable} holds on today's date; otherwise the current cost basis is used.
     */
    public MarginAssessment assess(PricingContext context, List<PricingLineResult> lines, MarginAssessment replayOf) {
        LocalDate today = LocalDate.now(clock);
        MarginAssessment replay = replayOf != null && replayable(replayOf, lines, today) ? replayOf : null;
        LocalDate pricedOn = replay != null ? LocalDate.parse(replay.getCostBasisAsOf()) : today;
        String currency = context.getCurrency();
        boolean ramp = lines.stream().anyMatch(CostBasisMarginEvaluator::hasPeriods);
        int duration = context.getDurationInMonths() != null && context.getDurationInMonths() > 0
                ? context.getDurationInMonths() : 12;

        List<LineMargin> assessed = new ArrayList<>();
        BigDecimal quoteNet = BigDecimal.ZERO;
        BigDecimal quoteCost = BigDecimal.ZERO;
        boolean complete = true;
        for (int index = 0; index < lines.size(); index++) {
            PricingLineResult line = lines.get(index);
            LineMargin recorded = recordedLine(replay, index, line.getProductExternalId());
            LineMargin margin = hasPeriods(line)
                    ? rampLine(index, line, pricedOn, currency, recorded, replay != null)
                    : flatLine(index, line, pricedOn, currency, recorded, replay != null);
            assessed.add(margin);
            if (margin.getNotApplicableReason() != null) {
                continue;
            }
            if (margin.getMarginFraction() == null) {
                complete = false;
                continue;
            }
            // Ramp lines carry term amounts; flat lines in a ramp quote are weighted over the term, as TCV is.
            BigDecimal weight = !hasPeriods(line) && ramp ? BigDecimal.valueOf(duration) : BigDecimal.ONE;
            quoteNet = quoteNet.add(margin.getNet().multiply(weight));
            quoteCost = quoteCost.add(margin.getCost().multiply(weight));
        }

        return MarginAssessment.builder()
                .source(MarginSource.COST_BASIS.wireValue())
                .costBasisAsOf(pricedOn.toString())
                .quoteMarginFraction(complete ? MetricsCalculator.marginFraction(quoteNet, quoteCost) : null)
                .lines(assessed)
                .replayedFromAuditId(replay == null ? null : replay.getReplayedFromAuditId())
                .build();
    }

    /**
     * Whether a recorded assessment may be reused for identical input on {@code pricedOn} (review finding M1): it
     * covers every line and ramp period of the input, none of them had a missing or other-currency cost, and every
     * recorded cost is still fresh on {@code pricedOn} ({@code effectiveFrom + 6 months > pricedOn}).
     */
    static boolean replayable(MarginAssessment recorded, List<PricingLineResult> lines, LocalDate pricedOn) {
        if (recorded.getCostBasisAsOf() == null || recorded.getLines() == null) {
            return false;
        }
        try {
            LocalDate.parse(recorded.getCostBasisAsOf());
        } catch (DateTimeParseException ex) {
            return false;
        }
        for (int index = 0; index < lines.size(); index++) {
            PricingLineResult line = lines.get(index);
            LineMargin margin = recordedLine(recorded, index, line.getProductExternalId());
            if (margin == null) {
                return false;
            }
            if (hasPeriods(line)) {
                for (PricedPeriod period : line.getPricedPeriods()) {
                    PeriodMargin recordedPeriod = recordedPeriod(margin, period.getSequence());
                    if (recordedPeriod == null || !usable(recordedPeriod.getMissingCostReason(),
                            recordedPeriod.getUnitCost(), recordedPeriod.getNotApplicableReason() != null, pricedOn)) {
                        return false;
                    }
                }
            } else if (!usable(margin.getMissingCostReason(), margin.getUnitCost(),
                    margin.getNotApplicableReason() != null, pricedOn)) {
                return false;
            }
        }
        return true;
    }

    private static boolean usable(String reason, CostEvidence evidence, boolean notApplicable, LocalDate pricedOn) {
        if (COST_MISSING.equals(reason) || COST_CURRENCY_MISMATCH.equals(reason)) {
            return false;
        }
        if (evidence == null || evidence.getUnitCost() == null || evidence.getEffectiveFrom() == null) {
            // Only a not-applicable unit may have no cost: its outcome does not depend on one.
            return notApplicable && evidence == null;
        }
        try {
            return !CostBasisStaleness.isStale(LocalDate.parse(evidence.getEffectiveFrom()), pricedOn);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private LineMargin flatLine(int index, PricingLineResult line, LocalDate pricedOn, String currency,
                                LineMargin recorded, boolean replay) {
        String product = line.getProductExternalId();
        CostEvidence evidence = replay ? recordedCost(recorded) : find(product, pricedOn, pricedOn);
        BigDecimal net = line.getFinalPrice();
        LineMargin.LineMarginBuilder margin = LineMargin.builder()
                .lineIndex(index)
                .productExternalId(product)
                .source(MarginSource.COST_BASIS.wireValue())
                .costAsOf(recorded != null && recorded.getCostAsOf() != null ? recorded.getCostAsOf() : pricedOn.toString())
                .net(net)
                .unitCost(evidence);
        if (net != null && net.signum() == 0) {
            // m1: margin does not apply, whatever the cost; out of the quote margin and never routed.
            return margin.notApplicableReason(NOT_APPLICABLE_ZERO_NET).build();
        }
        Unit unit = unit(evidence, currency, net, BigDecimal.valueOf(quantity(line.getQuantity())));
        return margin.cost(unit.cost).marginFraction(unit.fraction).missingCostReason(unit.reason).build();
    }

    private LineMargin rampLine(int index, PricingLineResult line, LocalDate pricedOn, String currency,
                                LineMargin recorded, boolean replay) {
        String product = line.getProductExternalId();
        List<PeriodMargin> periods = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        String firstReason = null;
        for (PricedPeriod period : line.getPricedPeriods()) {
            PeriodMargin recordedPeriod = recordedPeriod(recorded, period.getSequence());
            LocalDate startsOn = pricedOn.plusMonths(Math.max(0, startMonth(period) - 1));
            String costAsOf = recordedPeriod != null && recordedPeriod.getCostAsOf() != null
                    ? recordedPeriod.getCostAsOf() : startsOn.toString();
            // Selection on the period start, freshness on the pricing date (B1).
            CostEvidence evidence = replay ? recordedCost(recordedPeriod) : find(product, startsOn, pricedOn);
            int quantity = quantity(period.getQuantity());
            int months = period.getMonths() == null ? 0 : period.getMonths();
            // M2: the period amount, after any migration credit, not netMonthly × months.
            BigDecimal periodNet = period.getPeriodAmount();
            Unit unit = unit(evidence, currency, periodNet, BigDecimal.valueOf((long) quantity * months));
            periods.add(PeriodMargin.builder()
                    .sequence(period.getSequence())
                    .costAsOf(costAsOf)
                    .quantity(quantity)
                    .months(period.getMonths())
                    .net(periodNet)
                    .migrationCredit(period.getMigrationCredit())
                    .cost(unit.cost)
                    .marginFraction(unit.fraction)
                    .unitCost(evidence)
                    .missingCostReason(unit.reason)
                    .notApplicableReason(unit.notApplicable ? NOT_APPLICABLE_ZERO_NET : null)
                    .build());
            if (unit.reason != null) {
                if (firstReason == null) firstReason = unit.reason;
                continue;
            }
            // A zero-net period (released quantity, or fully credited) has no margin of its own, but its cost is
            // real and stays in the line and quote totals.
            net = net.add(periodNet);
            cost = cost.add(unit.cost);
        }
        LineMargin.LineMarginBuilder margin = LineMargin.builder()
                .lineIndex(index)
                .productExternalId(product)
                .source(MarginSource.COST_BASIS.wireValue())
                .periods(periods);
        if (firstReason != null) {
            return margin.missingCostReason(firstReason).build();
        }
        if (net.signum() == 0) {
            // m1, as for a flat line: a line whose net is exactly zero is margin-not-applicable.
            return margin.net(net).cost(cost).notApplicableReason(NOT_APPLICABLE_ZERO_NET).build();
        }
        if (net.signum() <= 0) {
            return margin.net(net).cost(cost).missingCostReason(NET_NOT_POSITIVE).build();
        }
        return margin.net(net).cost(cost).marginFraction(MetricsCalculator.marginFraction(net, cost)).build();
    }

    /** The cost recorded with the priced result; a replay never queries the lookup. */
    private static CostEvidence recordedCost(LineMargin recorded) {
        return recorded == null ? null : recorded.getUnitCost();
    }

    private static CostEvidence recordedCost(PeriodMargin recorded) {
        return recorded == null ? null : recorded.getUnitCost();
    }

    private CostEvidence find(String product, LocalDate effectiveOn, LocalDate pricedOn) {
        return lookup.find(product, effectiveOn, pricedOn).map(CostEvidence::of).orElse(null);
    }

    private static Unit unit(CostEvidence evidence, String currency, BigDecimal net, BigDecimal units) {
        if (evidence == null || evidence.getUnitCost() == null) {
            return new Unit(null, null, COST_MISSING, false);
        }
        if (currency == null || !currency.equalsIgnoreCase(evidence.getCurrency())) {
            return new Unit(null, null, COST_CURRENCY_MISMATCH, false);
        }
        BigDecimal cost = evidence.getUnitCost().multiply(units);
        if (net == null || net.signum() < 0) {
            return new Unit(cost, null, NET_NOT_POSITIVE, false);
        }
        if (net.signum() == 0) {
            return new Unit(cost, null, null, true);
        }
        return new Unit(cost, MetricsCalculator.marginFraction(net, cost), null, false);
    }

    private static LineMargin recordedLine(MarginAssessment replayOf, int index, String product) {
        if (replayOf == null || replayOf.getLines() == null) return null;
        return replayOf.getLines().stream()
                .filter(line -> Objects.equals(line.getLineIndex(), index)
                        && Objects.equals(line.getProductExternalId(), product))
                .findFirst().orElse(null);
    }

    private static PeriodMargin recordedPeriod(LineMargin recorded, Integer sequence) {
        if (recorded == null || recorded.getPeriods() == null) return null;
        return recorded.getPeriods().stream()
                .filter(period -> Objects.equals(period.getSequence(), sequence))
                .findFirst().orElse(null);
    }

    private static boolean hasPeriods(PricingLineResult line) {
        return line.getPricedPeriods() != null && !line.getPricedPeriods().isEmpty();
    }

    private static int startMonth(PricedPeriod period) {
        return period.getStartMonth() == null ? 1 : period.getStartMonth();
    }

    private static int quantity(Integer quantity) {
        return quantity == null ? 0 : quantity;
    }

    private record Unit(BigDecimal cost, BigDecimal fraction, String reason, boolean notApplicable) {
    }
}
