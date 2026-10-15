package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.cost.CostComponent;
import com.opencrm.pricing.cost.UnitCost;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineRequest;
import com.opencrm.pricing.waterfall.PricingLineResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CostBasisMarginEvaluatorTest {

    static final LocalDate PRICED_ON = LocalDate.of(2026, 10, 15);
    static final Clock CLOCK = Clock.fixed(PRICED_ON.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Test
    @DisplayName("flat line: (net − unit cost × quantity) / net, with the cost evidence recorded")
    void flatLine() {
        List<LocalDate> asked = new ArrayList<>();
        CostBasisLookup lookup = (product, asOf) -> {
            asked.add(asOf);
            return Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        };
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(flat("PROD-SEAT", 50, "2000.00")), null);

        LineMargin line = assessment.getLines().getFirst();
        assertThat(line.getMarginFraction()).isEqualByComparingTo("0.7380");
        assertThat(line.getNet()).isEqualByComparingTo("2000.00");
        assertThat(line.getCost()).isEqualByComparingTo("524.00");
        assertThat(line.getUnitCost().getUnitCost()).isEqualByComparingTo("10.48");
        assertThat(line.getUnitCost().getEffectiveFrom()).isEqualTo("2026-10-01");
        assertThat(line.getUnitCost().getVersion()).isEqualTo(1L);
        assertThat(line.getMissingCostReason()).isNull();
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.7380");
        assertThat(assessment.getSource()).isEqualTo("cost-basis");
        assertThat(asked).containsExactly(PRICED_ON);
    }

    @Test
    @DisplayName("ramp: each period uses the cost effective on its own start date (F-1 effective dating)")
    void periodEffectiveDating() {
        CostBasisLookup lookup = (product, asOf) -> asOf.isBefore(LocalDate.of(2027, 10, 1))
                ? Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1))
                : Optional.of(cost(product, "12.00", "USD", LocalDate.of(2027, 10, 1), 5));
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"),
                period(3, 25, 150, "37.20"));

        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);

        List<PeriodMargin> periods = assessment.getLines().getFirst().getPeriods();
        assertThat(periods).extracting(PeriodMargin::getCostAsOf)
                .containsExactly("2026-10-15", "2027-10-15", "2028-10-15");
        assertThat(periods.get(0).getUnitCost().getVersion()).isEqualTo(1L);
        assertThat(periods.get(1).getUnitCost().getVersion()).isEqualTo(5L);
        assertThat(periods.get(0).getMarginFraction()).isEqualByComparingTo("0.7380");
        // (37.20 − 12.00) / 37.20
        assertThat(periods.get(1).getMarginFraction()).isEqualByComparingTo("0.6774");
        // Quote level is weighted by the term amounts, consistent with the periods.
        BigDecimal net = new BigDecimal("2000.00").multiply(BigDecimal.valueOf(12))
                .add(new BigDecimal("3720.00").multiply(BigDecimal.valueOf(12)))
                .add(new BigDecimal("5580.00").multiply(BigDecimal.valueOf(12)));
        BigDecimal cost = new BigDecimal("10.48").multiply(BigDecimal.valueOf(50 * 12))
                .add(new BigDecimal("12.00").multiply(BigDecimal.valueOf(100 * 12)))
                .add(new BigDecimal("12.00").multiply(BigDecimal.valueOf(150 * 12)));
        BigDecimal expected = com.opencrm.pricing.execution.MetricsCalculator.marginFraction(net, cost);
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo(expected);
        assertThat(assessment.getLines().getFirst().getMarginFraction()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("mock v1 AC-02 ramp: 73.8% in period 1 and 71.8% at the 7% tier")
    void ac02MockMargins() {
        CostBasisLookup lookup = (product, asOf) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"),
                period(3, 25, 150, "37.20"));
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);
        assertThat(assessment.getLines().getFirst().getPeriods()).extracting(PeriodMargin::getMarginFraction)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("0.7380"), new BigDecimal("0.7182"), new BigDecimal("0.7182"));
        // (135600 − 10.48 × 3600) / 135600
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.7217");
    }

    @Test
    @DisplayName("an empty lookup (no row or stale) fails closed: no margin on the line or the quote")
    void missingCost() {
        MarginAssessment assessment = new CostBasisMarginEvaluator((product, asOf) -> Optional.empty(), CLOCK)
                .assess(context("USD", "PROD-SEAT", "PROD-NEW"),
                        List.of(flat("PROD-SEAT", 1, "40.00"), flat("PROD-NEW", 1, "100.00")), null);
        assertThat(assessment.getLines()).extracting(LineMargin::getMissingCostReason)
                .containsOnly(CostBasisMarginEvaluator.COST_MISSING);
        assertThat(assessment.getLines()).extracting(LineMargin::getMarginFraction).containsOnlyNulls();
        assertThat(assessment.getQuoteMarginFraction()).isNull();
    }

    @Test
    @DisplayName("one missing line removes the quote margin but keeps the other line's own margin")
    void partiallyMissing() {
        CostBasisLookup lookup = (product, asOf) -> "PROD-SEAT".equals(product)
                ? Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1)) : Optional.empty();
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT", "PROD-NEW"),
                        List.of(flat("PROD-SEAT", 1, "40.00"), flat("PROD-NEW", 1, "100.00")), null);
        assertThat(assessment.getLines().get(0).getMarginFraction()).isEqualByComparingTo("0.7380");
        assertThat(assessment.getLines().get(1).getMissingCostReason()).isEqualTo(CostBasisMarginEvaluator.COST_MISSING);
        assertThat(assessment.getQuoteMarginFraction()).isNull();
    }

    @Test
    @DisplayName("a cost in another currency has no conversion in pricing-service, so it fails closed")
    void otherCurrency() {
        CostBasisLookup lookup = (product, asOf) ->
                Optional.of(cost(product, "10.48", "EUR", LocalDate.of(2026, 10, 1), 1));
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(flat("PROD-SEAT", 1, "40.00")), null);
        LineMargin line = assessment.getLines().getFirst();
        assertThat(line.getMissingCostReason()).isEqualTo(CostBasisMarginEvaluator.COST_CURRENCY_MISMATCH);
        assertThat(line.getMarginFraction()).isNull();
        // The rejected cost is still recorded for audit.
        assertThat(line.getUnitCost().getCurrency()).isEqualTo("EUR");
        assertThat(assessment.getQuoteMarginFraction()).isNull();
    }

    @Test
    @DisplayName("a zero net has no margin and is reported, never divided")
    void zeroNet() {
        CostBasisLookup lookup = (product, asOf) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(flat("PROD-SEAT", 1, "0.00")), null);
        assertThat(assessment.getLines().getFirst().getMissingCostReason())
                .isEqualTo(CostBasisMarginEvaluator.NET_NOT_POSITIVE);
        assertThat(assessment.getQuoteMarginFraction()).isNull();
    }

    @Test
    @DisplayName("replay: a recorded assessment pins the original unit cost, whatever the lookup says now")
    void replayPinsRecordedCost() {
        CostBasisLookup original = (product, asOf) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        CostBasisLookup changed = (product, asOf) ->
                Optional.of(cost(product, "20.00", "USD", LocalDate.of(2026, 10, 1), 2));
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"));
        MarginAssessment first = new CostBasisMarginEvaluator(original, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);

        Clock later = Clock.offset(CLOCK, java.time.Duration.ofDays(200));
        MarginAssessment replayed = new CostBasisMarginEvaluator(changed, later)
                .assess(context("USD", "PROD-SEAT"), List.of(line), first.withReplayedFrom("audit-1"));

        assertThat(replayed.getLines().getFirst().getPeriods()).extracting(p -> p.getUnitCost().getVersion())
                .containsExactly(1L, 1L);
        assertThat(replayed.getLines().getFirst().getPeriods()).extracting(PeriodMargin::getCostAsOf)
                .containsExactlyElementsOf(first.getLines().getFirst().getPeriods().stream()
                        .map(PeriodMargin::getCostAsOf).toList());
        assertThat(replayed.getQuoteMarginFraction()).isEqualByComparingTo(first.getQuoteMarginFraction());
        assertThat(replayed.getReplayedFromAuditId()).isEqualTo("audit-1");
        assertThat(replayed.getCostBasisAsOf()).isEqualTo(first.getCostBasisAsOf());
    }

    @Test
    @DisplayName("replay never queries the lookup, even when a higher version lands on the same effective date")
    void replayNeverQueriesLookup() {
        List<LocalDate> asked = new ArrayList<>();
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"));
        MarginAssessment first = new CostBasisMarginEvaluator(
                (product, asOf) -> Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1)), CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);

        // L1's adapter returns the highest version effective on asOf: same date, version 2, new cost.
        CostBasisLookup sameDateNewerVersion = (product, asOf) -> {
            asked.add(asOf);
            return Optional.of(cost(product, "25.00", "USD", LocalDate.of(2026, 10, 1), 2));
        };
        MarginAssessment replayed = new CostBasisMarginEvaluator(sameDateNewerVersion, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), first.withReplayedFrom("audit-1"));

        assertThat(asked).isEmpty();
        assertThat(replayed.getLines().getFirst().getPeriods()).extracting(p -> p.getUnitCost().getVersion())
                .containsExactly(1L, 1L);
        assertThat(replayed.getQuoteMarginFraction()).isEqualByComparingTo(first.getQuoteMarginFraction());
    }

    @Test
    @DisplayName("replay with nothing recorded for a period fails closed instead of querying the lookup")
    void replayWithoutRecordFailsClosed() {
        List<LocalDate> asked = new ArrayList<>();
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"));
        MarginAssessment recordedWithoutPeriod2 = MarginAssessment.builder().source("cost-basis")
                .costBasisAsOf("2026-10-15").replayedFromAuditId("audit-1")
                .lines(List.of(LineMargin.builder().lineIndex(0).productExternalId("PROD-SEAT")
                        .periods(List.of(PeriodMargin.builder().sequence(1).costAsOf("2026-10-15")
                                .unitCost(CostEvidence.builder().productExternalId("PROD-SEAT")
                                        .unitCost(new BigDecimal("10.48")).currency("USD")
                                        .effectiveFrom("2026-10-01").version(1L).build()).build())).build()))
                .build();
        MarginAssessment replayed = new CostBasisMarginEvaluator((product, asOf) -> {
            asked.add(asOf);
            return Optional.empty();
        }, CLOCK).assess(context("USD", "PROD-SEAT"), List.of(line), recordedWithoutPeriod2);

        assertThat(asked).isEmpty();
        assertThat(replayed.getLines().getFirst().getPeriods().get(1).getMissingCostReason())
                .isEqualTo(CostBasisMarginEvaluator.COST_MISSING);
        assertThat(replayed.getQuoteMarginFraction()).isNull();
    }

    static UnitCost cost(String product, String unitCost, String currency, LocalDate from, long version) {
        return new UnitCost(product, "seat", new BigDecimal(unitCost), currency, from, version,
                Map.of(CostComponent.DIRECT, new BigDecimal(unitCost)));
    }

    static PricingContext context(String currency, String... products) {
        List<PricingLineRequest> lines = new ArrayList<>();
        for (String product : products) {
            lines.add(PricingLineRequest.builder().productExternalId(product).build());
        }
        return PricingContext.builder().quoteExternalId("Q-1").currency(currency).durationInMonths(36)
                .lineItems(lines).build();
    }

    static PricingLineResult flat(String product, int quantity, String finalPrice) {
        return PricingLineResult.builder().productExternalId(product).quantity(quantity)
                .finalPrice(new BigDecimal(finalPrice)).build();
    }

    static PricingLineResult ramp(String product, PricedPeriod... periods) {
        return PricingLineResult.builder().productExternalId(product).quantity(periods[0].getQuantity())
                .finalPrice(periods[0].getNetMonthly()).pricedPeriods(List.of(periods)).build();
    }

    static PricedPeriod period(int sequence, int startMonth, int quantity, String netUnit) {
        BigDecimal unit = new BigDecimal(netUnit);
        BigDecimal monthly = unit.multiply(BigDecimal.valueOf(quantity)).setScale(2, java.math.RoundingMode.HALF_UP);
        return PricedPeriod.builder().sequence(sequence).startMonth(startMonth).months(12).quantity(quantity)
                .netUnitPrice(unit).netMonthly(monthly).periodAmount(monthly.multiply(BigDecimal.valueOf(12)))
                .discountRate(BigDecimal.ZERO).build();
    }
}
