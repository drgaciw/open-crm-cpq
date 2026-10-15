package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.cost.CostBasisStaleness;
import com.opencrm.pricing.cost.CostComponent;
import com.opencrm.pricing.cost.UnitCost;
import com.opencrm.pricing.ramp.PricedPeriod;
import com.opencrm.pricing.ramp.migration.MigrationCreditPolicy;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CostBasisMarginEvaluatorTest {

    static final LocalDate PRICED_ON = LocalDate.of(2026, 10, 15);
    static final Clock CLOCK = Clock.fixed(PRICED_ON.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    @Test
    @DisplayName("flat line: (net − unit cost × quantity) / net, both lookup dates are the pricing date")
    void flatLine() {
        List<LocalDate[]> asked = new ArrayList<>();
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> {
            asked.add(new LocalDate[] {effectiveOn, pricedOn});
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
        assertThat(asked).hasSize(1);
        assertThat(asked.getFirst()).containsExactly(PRICED_ON, PRICED_ON);
    }

    @Test
    @DisplayName("ramp: each period selects the cost on its own start date and judges freshness on the pricing date")
    void periodEffectiveDating() {
        List<LocalDate> pricedOnSeen = new ArrayList<>();
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> {
            pricedOnSeen.add(pricedOn);
            return effectiveOn.isBefore(LocalDate.of(2027, 10, 1))
                    ? Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1))
                    : Optional.of(cost(product, "12.00", "USD", LocalDate.of(2027, 10, 1), 5));
        };
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"),
                period(3, 25, 150, "37.20"));

        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);

        List<PeriodMargin> periods = assessment.getLines().getFirst().getPeriods();
        assertThat(periods).extracting(PeriodMargin::getCostAsOf)
                .containsExactly("2026-10-15", "2027-10-15", "2028-10-15");
        assertThat(pricedOnSeen).containsOnly(PRICED_ON);
        assertThat(periods.get(0).getUnitCost().getVersion()).isEqualTo(1L);
        assertThat(periods.get(1).getUnitCost().getVersion()).isEqualTo(5L);
        assertThat(periods.get(0).getMarginFraction()).isEqualByComparingTo("0.7380");
        // (37.20 − 12.00) / 37.20
        assertThat(periods.get(1).getMarginFraction()).isEqualByComparingTo("0.6774");
        // Quote level is weighted by the period amounts, as TCV is.
        BigDecimal net = new BigDecimal("24000.00").add(new BigDecimal("44640.00")).add(new BigDecimal("66960.00"));
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
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"),
                period(3, 25, 150, "37.20"));
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);
        assertThat(assessment.getLines().getFirst().getPeriods()).extracting(PeriodMargin::getMarginFraction)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("0.7380"), new BigDecimal("0.7182"), new BigDecimal("0.7182"));
        // Period net is the period amount: 24000.00, 44640.00, 66960.00.
        assertThat(assessment.getLines().getFirst().getPeriods()).extracting(p -> p.getNet().toPlainString())
                .containsExactly("24000.00", "44640.00", "66960.00");
        // (135600 − 10.48 × 3600) / 135600
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.7217");
    }

    @Test
    @DisplayName("M2: a hybrid migration credit lowers the period net, so the credited period's margin is lower")
    void migrationCreditLowersPeriodMargin() {
        // The hybrid migration fixture's declining ONPREM_SUPPORT member: 10 licences at 100.00 for 12 months,
        // book value 10,000.00, so a 30% credit of 3,000.00 on the first period's 12,000.00.
        MigrationCreditPolicy.Credit credit = new MigrationCreditPolicy().calculate(
                new BigDecimal("10000.00"), new BigDecimal("12000.00"), new BigDecimal("12000.00"));
        assertThat(credit.adjustedPeriodAmount()).isEqualByComparingTo("9000.00");
        PricedPeriod credited = PricedPeriod.builder().sequence(1).startMonth(1).months(12).quantity(10)
                .netUnitPrice(new BigDecimal("100.00")).netMonthly(new BigDecimal("1000.00"))
                .grossPeriodAmount(new BigDecimal("12000.00")).migrationCredit(credit.effectiveCredit())
                .periodAmount(credit.adjustedPeriodAmount()).discountRate(BigDecimal.ZERO).build();
        PricedPeriod released = PricedPeriod.builder().sequence(2).startMonth(13).months(24).quantity(0)
                .netUnitPrice(new BigDecimal("100.00")).netMonthly(new BigDecimal("0.00"))
                .periodAmount(new BigDecimal("0.00")).discountRate(BigDecimal.ZERO).build();
        // Mock v1 ONPREM_SUPPORT: 58.33 per licence per month.
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "58.33", "USD", LocalDate.of(2026, 10, 1), 1));

        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SUPPORT"), List.of(ramp("PROD-SUPPORT", credited, released)), null);

        PeriodMargin first = assessment.getLines().getFirst().getPeriods().getFirst();
        // Gross 12,000.00 minus the 3,000.00 credit: the period net is 9,000.00, not 1,000.00 × 12.
        assertThat(first.getNet()).isEqualByComparingTo("9000.00");
        assertThat(first.getMigrationCredit()).isEqualByComparingTo("3000.00");
        assertThat(first.getCost()).isEqualByComparingTo("6999.60");
        // (9000.00 − 6999.60) / 9000.00, against 0.4167 on the uncredited 12,000.00.
        assertThat(first.getMarginFraction()).isEqualByComparingTo("0.2222");
        assertThat(com.opencrm.pricing.execution.MetricsCalculator.marginFraction(
                new BigDecimal("12000.00"), new BigDecimal("6999.60"))).isEqualByComparingTo("0.4167");
        // The released period has zero net and zero cost: not applicable, not a missing cost.
        PeriodMargin second = assessment.getLines().getFirst().getPeriods().get(1);
        assertThat(second.getMarginFraction()).isNull();
        assertThat(second.getMissingCostReason()).isNull();
        assertThat(second.getNotApplicableReason()).isEqualTo(CostBasisMarginEvaluator.NOT_APPLICABLE_ZERO_NET);
        // Quote margin weighted by period amounts, consistent with TCV.
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.2222");
    }

    @Test
    @DisplayName("an empty lookup (no row or stale) fails closed: no margin on the line or the quote")
    void missingCost() {
        MarginAssessment assessment = new CostBasisMarginEvaluator((product, effectiveOn, pricedOn) -> Optional.empty(), CLOCK)
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
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> "PROD-SEAT".equals(product)
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
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
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
    @DisplayName("m1: a line with net exactly 0 is not applicable, excluded from the quote margin, with the reason traced")
    void zeroNetLineIsNotApplicable() {
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT", "PROD-FREE"),
                        List.of(flat("PROD-SEAT", 1, "40.00"), flat("PROD-FREE", 1, "0.00")), null);
        LineMargin free = assessment.getLines().get(1);
        assertThat(free.getNotApplicableReason()).isEqualTo(CostBasisMarginEvaluator.NOT_APPLICABLE_ZERO_NET);
        assertThat(free.getMissingCostReason()).isNull();
        assertThat(free.getMarginFraction()).isNull();
        // The quote margin is the seat line's alone: the zero-net line neither blanks nor dilutes it.
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.7380");
    }

    @Test
    @DisplayName("m1: a negative net still has no margin and reports MARGIN_NET_NOT_POSITIVE")
    void negativeNetIsNotPositive() {
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(flat("PROD-SEAT", 1, "-5.00")), null);
        assertThat(assessment.getLines().getFirst().getMissingCostReason())
                .isEqualTo(CostBasisMarginEvaluator.NET_NOT_POSITIVE);
        assertThat(assessment.getLines().getFirst().getNotApplicableReason()).isNull();
        assertThat(assessment.getQuoteMarginFraction()).isNull();
    }

    // ---- replay (M1) ----

    @Test
    @DisplayName("replay: a fresh, complete recorded assessment pins the original unit cost, whatever the lookup says now")
    void replayPinsRecordedCost() {
        CostBasisLookup original = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        CostBasisLookup changed = (product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "20.00", "USD", LocalDate.of(2026, 10, 1), 2));
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"));
        MarginAssessment first = new CostBasisMarginEvaluator(original, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);

        // 120 days later, 2027-02-12: the recorded cost (effective 2026-10-01) is still fresh.
        Clock later = Clock.offset(CLOCK, java.time.Duration.ofDays(120));
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
    @DisplayName("M1: an identical fresh replay never queries the lookup, even when a higher version lands on the same date")
    void replayNeverQueriesLookup() {
        List<LocalDate> asked = new ArrayList<>();
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"));
        MarginAssessment first = new CostBasisMarginEvaluator((product, effectiveOn, pricedOn) ->
                Optional.of(cost(product, "10.48", "USD", LocalDate.of(2026, 10, 1), 1)), CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);

        // L1's adapter returns the highest version effective on the date: same date, version 2, new cost.
        CostBasisLookup sameDateNewerVersion = (product, effectiveOn, pricedOn) -> {
            asked.add(effectiveOn);
            return Optional.of(cost(product, "25.00", "USD", LocalDate.of(2026, 10, 1), 2));
        };
        MarginAssessment replayed = new CostBasisMarginEvaluator(sameDateNewerVersion, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), first.withReplayedFrom("audit-1"));

        assertThat(asked).isEmpty();
        assertThat(replayed.getLines().getFirst().getPeriods()).extracting(p -> p.getUnitCost().getVersion())
                .containsExactly(1L, 1L);
        assertThat(replayed.getQuoteMarginFraction()).isEqualByComparingTo(first.getQuoteMarginFraction());
        assertThat(replayed.getReplayedFromAuditId()).isEqualTo("audit-1");
    }

    @Test
    @DisplayName("M1: a recorded missing cost is not pinned; once the cost is loaded a re-execute gives a real margin")
    void missingThenLoadedGivesRealMargin() {
        AtomicReference<UnitCost> stored = new AtomicReference<>();
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> Optional.ofNullable(stored.get());
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"),
                period(3, 25, 150, "37.20"));
        MarginAssessment missing = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);
        assertThat(missing.getQuoteMarginFraction()).isNull();

        stored.set(cost("PROD-SEAT", "10.48", "USD", LocalDate.of(2026, 10, 1), 1));
        MarginAssessment again = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), missing.withReplayedFrom("audit-1"));

        assertThat(again.getReplayedFromAuditId()).isNull();
        assertThat(again.getLines().getFirst().getPeriods()).extracting(PeriodMargin::getMissingCostReason)
                .containsOnlyNulls();
        assertThat(again.getQuoteMarginFraction()).isEqualByComparingTo("0.7217");
    }

    @Test
    @DisplayName("M1: an October 2026 pricing re-executed on 2027-04-01 is not pinned: its cost is stale, so it fails closed")
    void staleRecordedCostFailsClosedOnReexecute() {
        // Honours the port: no cost once the newest row is stale on the pricing date.
        UnitCost v1 = cost("PROD-SEAT", "10.48", "USD", LocalDate.of(2026, 10, 1), 1);
        List<LocalDate> pricedOnSeen = new ArrayList<>();
        CostBasisLookup lookup = (product, effectiveOn, pricedOn) -> {
            pricedOnSeen.add(pricedOn);
            return CostBasisStaleness.isStale(v1.effectiveFrom(), pricedOn) ? Optional.empty() : Optional.of(v1);
        };
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"));
        MarginAssessment october = new CostBasisMarginEvaluator(lookup, CLOCK)
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);
        assertThat(october.getQuoteMarginFraction()).isNotNull();

        LocalDate april = LocalDate.of(2027, 4, 1);
        Clock aprilClock = Clock.fixed(april.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        MarginAssessment reexecuted = new CostBasisMarginEvaluator(lookup, aprilClock)
                .assess(context("USD", "PROD-SEAT"), List.of(line), october.withReplayedFrom("audit-1"));

        assertThat(reexecuted.getReplayedFromAuditId()).isNull();
        assertThat(reexecuted.getCostBasisAsOf()).isEqualTo("2027-04-01");
        assertThat(pricedOnSeen).contains(april);
        assertThat(reexecuted.getLines().getFirst().getPeriods()).extracting(PeriodMargin::getMissingCostReason)
                .containsOnly(CostBasisMarginEvaluator.COST_MISSING);
        assertThat(reexecuted.getQuoteMarginFraction()).isNull();
    }

    @Test
    @DisplayName("M1: a recorded assessment missing a period is not pinned; the input is priced on the current cost basis")
    void incompleteRecordIsReevaluated() {
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
        MarginAssessment replayed = new CostBasisMarginEvaluator((product, effectiveOn, pricedOn) -> {
            asked.add(effectiveOn);
            return Optional.empty();
        }, CLOCK).assess(context("USD", "PROD-SEAT"), List.of(line), recordedWithoutPeriod2);

        assertThat(asked).hasSize(2);
        assertThat(replayed.getReplayedFromAuditId()).isNull();
        assertThat(replayed.getLines().getFirst().getPeriods()).extracting(PeriodMargin::getMissingCostReason)
                .containsOnly(CostBasisMarginEvaluator.COST_MISSING);
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
