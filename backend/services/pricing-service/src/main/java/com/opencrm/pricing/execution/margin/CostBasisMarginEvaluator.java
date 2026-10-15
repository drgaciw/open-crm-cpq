package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.execution.MetricsCalculator;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Derives cost-basis margin (finance decision F-1) for every line and every ramp period of a priced quote.
 *
 * <p>Margin is {@code (net − unit cost × quantity) / net}, a fraction. A ramp period uses the cost effective on its
 * own start date, counted from the pricing date (no contract start date reaches pricing-service). There is no
 * margin, never a substitute, when the lookup is empty (no row, or a stale one), when the cost is in a currency
 * other than the price's (pricing-service has no conversion), or when the net is not positive.
 *
 * <p>When an earlier assessment of identical input is supplied, its recorded unit costs and pricing date are
 * used and the lookup is never called, so a replay returns the cost and margin the quote was priced with even if
 * a newer cost version has since landed on the same effective date.
 */
@Component
public class CostBasisMarginEvaluator {

    /** The lookup was empty: no cost row, or the newest row is stale. */
    public static final String COST_MISSING = "MARGIN_COST_MISSING";
    /** The cost's currency differs from the price's and pricing-service cannot convert it. */
    public static final String COST_CURRENCY_MISMATCH = "MARGIN_COST_CURRENCY_MISMATCH";
    /** The net is zero or negative, so a margin fraction is undefined. */
    public static final String NET_NOT_POSITIVE = "MARGIN_NET_NOT_POSITIVE";

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
     * @param replayOf an earlier assessment of the same input, or {@code null} to use the current cost basis
     */
    public MarginAssessment assess(PricingContext context, List<PricingLineResult> lines, MarginAssessment replayOf) {
        LocalDate pricedOn = replayOf != null && replayOf.getCostBasisAsOf() != null
                ? LocalDate.parse(replayOf.getCostBasisAsOf())
                : LocalDate.now(clock);
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
            LineMargin recorded = recordedLine(replayOf, index, line.getProductExternalId());
            LineMargin margin = hasPeriods(line)
                    ? rampLine(index, line, pricedOn, currency, recorded, replayOf != null)
                    : flatLine(index, line, pricedOn, currency, recorded, replayOf != null);
            assessed.add(margin);
            if (margin.getMarginFraction() == null) {
                complete = false;
                continue;
            }
            // Flat lines in a ramp quote are weighted over the term, as MetricsCalculator weights TCV.
            BigDecimal weight = !hasPeriods(line) && ramp ? BigDecimal.valueOf(duration) : BigDecimal.ONE;
            quoteNet = quoteNet.add(margin.getNet().multiply(weight));
            quoteCost = quoteCost.add(margin.getCost().multiply(weight));
        }

        return MarginAssessment.builder()
                .source(MarginSource.COST_BASIS.wireValue())
                .costBasisAsOf(pricedOn.toString())
                .quoteMarginFraction(complete && !assessed.isEmpty()
                        ? MetricsCalculator.marginFraction(quoteNet, quoteCost) : null)
                .lines(assessed)
                .replayedFromAuditId(replayOf == null ? null : replayOf.getReplayedFromAuditId())
                .build();
    }

    private LineMargin flatLine(int index, PricingLineResult line, LocalDate pricedOn, String currency,
                                LineMargin recorded, boolean replay) {
        String product = line.getProductExternalId();
        CostEvidence evidence = replay ? recordedCost(recorded) : find(product, pricedOn);
        Unit unit = unit(evidence, currency, line.getFinalPrice(), quantity(line.getQuantity()));
        return LineMargin.builder()
                .lineIndex(index)
                .productExternalId(product)
                .source(MarginSource.COST_BASIS.wireValue())
                .costAsOf(recorded != null && recorded.getCostAsOf() != null ? recorded.getCostAsOf() : pricedOn.toString())
                .net(line.getFinalPrice())
                .cost(unit.cost)
                .marginFraction(unit.fraction)
                .unitCost(evidence)
                .missingCostReason(unit.reason)
                .build();
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
            CostEvidence evidence = replay ? recordedCost(recordedPeriod) : find(product, startsOn);
            int quantity = quantity(period.getQuantity());
            Unit unit = unit(evidence, currency, period.getNetMonthly(), quantity);
            periods.add(PeriodMargin.builder()
                    .sequence(period.getSequence())
                    .costAsOf(costAsOf)
                    .quantity(quantity)
                    .months(period.getMonths())
                    .net(period.getNetMonthly())
                    .cost(unit.cost)
                    .marginFraction(unit.fraction)
                    .unitCost(evidence)
                    .missingCostReason(unit.reason)
                    .build());
            if (unit.reason != null) {
                if (firstReason == null) firstReason = unit.reason;
                continue;
            }
            BigDecimal months = BigDecimal.valueOf(period.getMonths() == null ? 0 : period.getMonths());
            net = net.add(period.getNetMonthly().multiply(months));
            cost = cost.add(unit.cost.multiply(months));
        }
        boolean complete = firstReason == null;
        return LineMargin.builder()
                .lineIndex(index)
                .productExternalId(product)
                .source(MarginSource.COST_BASIS.wireValue())
                .net(complete ? net : null)
                .cost(complete ? cost : null)
                .marginFraction(complete ? MetricsCalculator.marginFraction(net, cost) : null)
                .missingCostReason(firstReason)
                .periods(periods)
                .build();
    }

    /**
     * A replay never queries the lookup: a newer version on the same effective date would otherwise change the
     * cost. It uses only the cost recorded with the priced result, and a unit with nothing recorded fails closed.
     */
    private static CostEvidence recordedCost(LineMargin recorded) {
        return recorded == null ? null : recorded.getUnitCost();
    }

    private static CostEvidence recordedCost(PeriodMargin recorded) {
        return recorded == null ? null : recorded.getUnitCost();
    }

    private CostEvidence find(String product, LocalDate asOf) {
        return lookup.find(product, asOf).map(CostEvidence::of).orElse(null);
    }

    private static Unit unit(CostEvidence evidence, String currency, BigDecimal net, int quantity) {
        if (evidence == null || evidence.getUnitCost() == null) {
            return new Unit(null, null, COST_MISSING);
        }
        if (currency == null || !currency.equalsIgnoreCase(evidence.getCurrency())) {
            return new Unit(null, null, COST_CURRENCY_MISMATCH);
        }
        BigDecimal cost = evidence.getUnitCost().multiply(BigDecimal.valueOf(quantity));
        if (net == null || net.signum() <= 0) {
            return new Unit(cost, null, NET_NOT_POSITIVE);
        }
        return new Unit(cost, MetricsCalculator.marginFraction(net, cost), null);
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

    private record Unit(BigDecimal cost, BigDecimal fraction, String reason) {
    }
}
