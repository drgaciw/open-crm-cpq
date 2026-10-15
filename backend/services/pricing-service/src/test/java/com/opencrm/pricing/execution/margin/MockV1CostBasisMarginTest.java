package com.opencrm.pricing.execution.margin;

import com.opencrm.pricing.cost.CostBasisLookup;
import com.opencrm.pricing.cost.CostBasisStaleness;
import com.opencrm.pricing.cost.CostComponent;
import com.opencrm.pricing.cost.UnitCost;
import com.opencrm.pricing.waterfall.PricingLineResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.context;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.flat;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.period;
import static com.opencrm.pricing.execution.margin.CostBasisMarginEvaluatorTest.ramp;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review blocker B1: the evaluator must select each period's cost on the period start and judge freshness on the
 * pricing date. The fake below honours both dates exactly as the port documents, over the approved mock v1 export
 * (effective 2026-10-01, stale from 2027-04-01). Judging freshness on a future period start would fail AC-02's
 * periods 2 and 3 (starting 2027-10-15 and 2028-10-15) as stale.
 */
@Tag("unit")
@DisplayName("B1: mock v1 through a lookup honouring effectiveOn and pricedOn")
class MockV1CostBasisMarginTest {

    private static final LocalDate PRICED_ON = LocalDate.of(2026, 10, 15);

    @Test
    @DisplayName("AC-02 priced 2026-10-15 on mock v1: 0.7380 / 0.7182 / 0.7182, quote 0.7217, no MARGIN_COST_MISSING")
    void ac02OnMockV1() {
        MockV1 lookup = MockV1.load();
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"),
                period(3, 25, 150, "37.20"));

        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, clock(PRICED_ON))
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);

        List<PeriodMargin> periods = assessment.getLines().getFirst().getPeriods();
        assertThat(periods).extracting(p -> p.getMarginFraction().toPlainString())
                .containsExactly("0.7380", "0.7182", "0.7182");
        assertThat(periods).extracting(PeriodMargin::getMissingCostReason).containsOnlyNulls();
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.7217");
        assertThat(periods).extracting(p -> p.getUnitCost().getUnitCost().toPlainString()).containsOnly("10.4800");
        // Selection on each period start, freshness always on the pricing date.
        assertThat(lookup.calls).containsExactly(
                new Call("PROD-SEAT", LocalDate.of(2026, 10, 15), PRICED_ON),
                new Call("PROD-SEAT", LocalDate.of(2027, 10, 15), PRICED_ON),
                new Call("PROD-SEAT", LocalDate.of(2028, 10, 15), PRICED_ON));
    }

    @Test
    @DisplayName("a flat line looks up with both dates on the pricing date")
    void flatLineUsesPricingDateForBoth() {
        MockV1 lookup = MockV1.load();
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, clock(PRICED_ON))
                .assess(context("USD", "PROD-SEAT"), List.of(flat("PROD-SEAT", 50, "2000.00")), null);
        assertThat(assessment.getQuoteMarginFraction()).isEqualByComparingTo("0.7380");
        assertThat(lookup.calls).containsExactly(new Call("PROD-SEAT", PRICED_ON, PRICED_ON));
    }

    @Test
    @DisplayName("the same quote priced on 2027-04-01 fails closed: mock v1 is stale on the pricing date")
    void staleOnPricingDateFailsClosed() {
        MockV1 lookup = MockV1.load();
        PricingLineResult line = ramp("PROD-SEAT", period(1, 1, 50, "40.00"), period(2, 13, 100, "37.20"));
        MarginAssessment assessment = new CostBasisMarginEvaluator(lookup, clock(LocalDate.of(2027, 4, 1)))
                .assess(context("USD", "PROD-SEAT"), List.of(line), null);
        assertThat(assessment.getLines().getFirst().getPeriods()).extracting(PeriodMargin::getMissingCostReason)
                .containsOnly(CostBasisMarginEvaluator.COST_MISSING);
        assertThat(assessment.getQuoteMarginFraction()).isNull();
    }

    static Clock clock(LocalDate on) {
        return Clock.fixed(on.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    record Call(String product, LocalDate effectiveOn, LocalDate pricedOn) {
    }

    /**
     * The approved mock v1 export (test fixture), with the port's two-date semantics: the newest rows effective on
     * or before {@code effectiveOn} are selected; the newest rows effective on or before {@code pricedOn} must not
     * be stale on {@code pricedOn}.
     */
    static final class MockV1 implements CostBasisLookup {

        final List<Call> calls = new ArrayList<>();
        private final List<UnitCost> costs;

        private MockV1(List<UnitCost> costs) {
            this.costs = costs;
        }

        static MockV1 load() {
            Map<String, Map<CostComponent, BigDecimal>> components = new java.util.LinkedHashMap<>();
            Map<String, String[]> heads = new java.util.HashMap<>();
            try (InputStream in = MockV1.class.getResourceAsStream("/fixtures/cost-basis/cpq-cost-export-v1.csv")) {
                String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R");
                for (int i = 1; i < lines.length; i++) {
                    if (lines[i].isBlank()) continue;
                    // productExternalId,component,billingUnit,unitCost,currency,effectiveFrom,version,"approvedBy"
                    String[] f = lines[i].split(",", 8);
                    String key = f[0] + "|" + f[5] + "|" + f[6];
                    heads.putIfAbsent(key, f);
                    components.computeIfAbsent(key, k -> new EnumMap<>(CostComponent.class))
                            .put(CostComponent.valueOf(f[1]), new BigDecimal(f[3]));
                }
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
            List<UnitCost> costs = new ArrayList<>();
            components.forEach((key, parts) -> {
                String[] f = heads.get(key);
                BigDecimal total = parts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                costs.add(new UnitCost(f[0], f[2], total, f[4], LocalDate.parse(f[5]), Long.parseLong(f[6]), parts));
            });
            return new MockV1(costs);
        }

        @Override
        public Optional<UnitCost> find(String productExternalId, LocalDate effectiveOn, LocalDate pricedOn) {
            calls.add(new Call(productExternalId, effectiveOn, pricedOn));
            Optional<UnitCost> newestPriced = newest(productExternalId, pricedOn);
            if (newestPriced.isEmpty() || CostBasisStaleness.isStale(newestPriced.get().effectiveFrom(), pricedOn)) {
                return Optional.empty();
            }
            return newest(productExternalId, effectiveOn);
        }

        private Optional<UnitCost> newest(String product, LocalDate on) {
            return costs.stream()
                    .filter(cost -> cost.productExternalId().equals(product) && !cost.effectiveFrom().isAfter(on))
                    .max(Comparator.comparing(UnitCost::effectiveFrom).thenComparingLong(UnitCost::version));
        }
    }
}
