package com.opencrm.quote.ramp;

import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ramp escalator AC-04")
class RampEscalatorExpanderTest {

    @Test
    void rejectsHugeExpansionAndExtensionBeforeAllocation() {
        RampEscalatorDto monthly = escalator(RampEscalatorMode.AMOUNT, "40", "1", 1);
        assertThatThrownBy(() -> expander.expand(Integer.MAX_VALUE, monthly, 1, 12))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.TOO_MANY_PERIODS));
        assertThatThrownBy(() -> expander.extend(goldenThirtySix(), Integer.MAX_VALUE, monthly, 12))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.TOO_MANY_PERIODS));
    }

    private RampEscalatorExpander expander;

    @BeforeEach
    void setUp() {
        expander = new RampEscalatorExpander();
    }

    @Test
    @DisplayName("5 percent compounds to 40.0000, 42.0000, 44.1000 at scale 4")
    void percentCompoundsAtScale4() {
        List<RampStepDto> steps = expander.expand(36, escalator(RampEscalatorMode.PERCENT, "40", "5", 12), 3);

        assertThat(prices(steps)).containsExactly("40.0000", "42.0000", "44.1000");
        assertThat(steps).allSatisfy(step -> {
            assertThat(step.getUnitPrice().scale()).isEqualTo(4);
            assertThat(step.getPriceBasis()).isEqualTo(RampPriceBasis.ABSOLUTE);
            assertThat(step.getQuantity()).isEqualTo(3);
            assertThat(step.getDiscountFraction()).isNull();
        });
        assertThat(steps).extracting(RampStepDto::getMonths).containsExactly(12, 12, 12);
        assertThat(steps).extracting(RampStepDto::getStartMonth).containsExactly(1, 13, 25);
        assertThat(steps).extracting(RampStepDto::getSequence).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("amount +5 yields 40, 45, 50 at scale 4")
    void amountAddsAtScale4() {
        List<RampStepDto> steps = expander.expand(36, escalator(RampEscalatorMode.AMOUNT, "40", "5", 12), 1);

        assertThat(prices(steps)).containsExactly("40.0000", "45.0000", "50.0000");
        assertThat(steps).allSatisfy(step -> assertThat(step.getUnitPrice().scale()).isEqualTo(4));
    }

    @Test
    @DisplayName("percent 1.1125 on 2.0000 rounds half-up to 2.0223")
    void percentHalfUp() {
        List<RampStepDto> steps = expander.expand(
                24,
                escalator(RampEscalatorMode.PERCENT, "2.0000", "1.1125", 12),
                1);

        assertThat(prices(steps)).containsExactly("2.0000", "2.0223");
    }

    @Test
    @DisplayName("a start price of 1.00005 rounds half-up to 1.0001")
    void startPriceHalfUp() {
        List<RampStepDto> steps = expander.expand(
                12,
                escalator(RampEscalatorMode.AMOUNT, "1.00005", "0", 12),
                1);

        assertThat(prices(steps)).containsExactly("1.0001");
    }

    @Test
    @DisplayName("a term that everyMonths does not divide reports only RAMP_TERM_MISMATCH")
    void indivisibleTermReportsMismatch() {
        assertThatThrownBy(() -> expander.expand(14, escalator(RampEscalatorMode.PERCENT, "40", "5", 12), 1))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getReasons()).containsExactly(RampReasons.TERM_MISMATCH);
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_INVALID);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });
    }

    @Test
    @DisplayName("a negative start price or amount reports RAMP_NEGATIVE_VALUE")
    void negativeInputs() {
        assertThatThrownBy(() -> expander.expand(12, escalator(RampEscalatorMode.AMOUNT, "-1", "5", 12), 1))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.NEGATIVE_VALUE));
    }

    @Test
    @DisplayName("AC-532-1: 36-month yearly 7 percent from 42.80 is three periods at the ADR scale-4 prices")
    void thirtySixMonthYearlySevenPercent() {
        List<RampStepDto> steps = expander.expand(36, escalator(RampEscalatorMode.PERCENT, "42.80", "7", 12), 1);

        assertThat(steps).hasSize(3);
        assertThat(prices(steps)).containsExactly("42.8000", "45.7960", "49.0017");
        assertThat(steps).extracting(RampStepDto::getStartMonth).containsExactly(1, 13, 25);
        assertThat(steps).extracting(RampStepDto::getMonths).containsExactly(12, 12, 12);
        assertThat(steps).allSatisfy(step -> assertThat(step.getUnitPrice().scale()).isEqualTo(4));
    }

    @Test
    @DisplayName("AC-532-2: amount +5 on 100 for 36 months yearly is 100, 105, 110")
    void amountThirtySixFromOneHundred() {
        List<RampStepDto> steps = expander.expand(36, escalator(RampEscalatorMode.AMOUNT, "100", "5", 12), 1);

        assertThat(prices(steps)).containsExactly("100.0000", "105.0000", "110.0000");
        assertThat(steps).allSatisfy(step -> assertThat(step.getUnitPrice().scale()).isEqualTo(4));
    }

    @Test
    @DisplayName("AC-532-3: extending 36 to 60 appends two steps compounded from the last unit")
    void extendThirtySixToSixtyAppendsCompoundedSteps() {
        List<RampStepDto> existing = goldenThirtySix();

        List<RampStepDto> result = expander.extend(existing, 60, sevenPercent());

        assertThat(result).hasSize(5);
        assertThat(result.get(0)).isSameAs(existing.get(0));
        assertThat(result.get(1)).isSameAs(existing.get(1));
        assertThat(result.get(2)).isSameAs(existing.get(2));
        assertThat(prices(result)).containsExactly(
                "42.8000", "45.7960", "49.0017", "52.4318", "56.1020");
        assertThat(result).extracting(RampStepDto::getSequence).containsExactly(1, 2, 3, 4, 5);
        assertThat(result).extracting(RampStepDto::getStartMonth).containsExactly(1, 13, 25, 37, 49);
        assertThat(result).extracting(RampStepDto::getMonths).containsExactly(12, 12, 12, 12, 12);
        assertThat(result.get(3).getPriceBasis()).isEqualTo(RampPriceBasis.ABSOLUTE);
        assertThat(result.get(3).getDiscountFraction()).isNull();
        assertThat(result.get(3).getQuantity()).isEqualTo(2);
        assertThat(result.get(3).getUnitPrice().scale()).isEqualTo(4);
        assertThat(result.get(4).getUnitPrice().scale()).isEqualTo(4);
    }

    @Test
    @DisplayName("AC-532-4: extend keeps hand-edited prices and compounds from the edited last unit")
    void extendPreservesHandEditedPrices() {
        List<RampStepDto> existing = List.of(
                absolute(1, 1, 12, "42.8000"),
                absolute(2, 13, 12, "44.0000"),
                absolute(3, 25, 12, "47.0000"));

        List<RampStepDto> result = expander.extend(existing, 60, sevenPercent());

        assertThat(result.get(0)).isSameAs(existing.get(0));
        assertThat(result.get(1)).isSameAs(existing.get(1));
        assertThat(result.get(2)).isSameAs(existing.get(2));
        assertThat(prices(result)).containsExactly(
                "42.8000", "44.0000", "47.0000", "50.2900", "53.8103");
        assertThat(result.get(3).getSequence()).isEqualTo(4);
        assertThat(result.get(3).getStartMonth()).isEqualTo(37);
    }

    @Test
    @DisplayName("AC-532-4 amount mode adds to the last stored unit")
    void extendAmountAddsToLastUnit() {
        List<RampStepDto> existing = List.of(absolute(1, 1, 12, "100.0000"), absolute(2, 13, 24, "110.0000"));

        List<RampStepDto> result = expander.extend(
                existing, 48, escalator(RampEscalatorMode.AMOUNT, "100", "5", 12));

        assertThat(result).hasSize(3);
        assertThat(result.get(2).getUnitPrice().toPlainString()).isEqualTo("115.0000");
        assertThat(result.get(2).getStartMonth()).isEqualTo(37);
        assertThat(result.get(2).getSequence()).isEqualTo(3);
        assertThat(result.get(0)).isSameAs(existing.get(0));
    }

    @Test
    @DisplayName("AC-532-5: a term that is not longer, or an extension the step does not divide, is RAMP_TERM_MISMATCH")
    void extendRejectsTermThatDoesNotGrowByWholeSteps() {
        List<RampStepDto> existing = goldenThirtySix();

        assertThatThrownBy(() -> expander.extend(existing, 36, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.TERM_MISMATCH));
        assertThatThrownBy(() -> expander.extend(existing, 24, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.TERM_MISMATCH));
        assertThatThrownBy(() -> expander.extend(existing, 42, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.TERM_MISMATCH));
    }

    @Test
    @DisplayName("AC-532-5: a gap or an overlap in the existing steps is refused with that reason")
    void extendRejectsNonContiguousSteps() {
        assertThatThrownBy(() -> expander.extend(List.of(
                        absolute(1, 1, 12, "42.8000"),
                        absolute(2, 14, 12, "45.0000")),
                48, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.GAP));

        assertThatThrownBy(() -> expander.extend(List.of(
                        absolute(1, 1, 12, "42.8000"),
                        absolute(2, 12, 12, "45.0000")),
                48, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.OVERLAP));
    }

    @Test
    @DisplayName("AC-532-5: a negative escalator amount or unit price is RAMP_NEGATIVE_VALUE")
    void extendRejectsNegativeAmount() {
        assertThatThrownBy(() -> expander.extend(
                goldenThirtySix(), 48, escalator(RampEscalatorMode.PERCENT, "42.80", "-1", 12)))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.NEGATIVE_VALUE));

        assertThatThrownBy(() -> expander.extend(
                List.of(absolute(1, 1, 36, "-1.0000")), 48, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.NEGATIVE_VALUE));
    }

    @Test
    @DisplayName("AC-532-5: steps that do not start at month 1 are a gap, and an empty list is an empty period")
    void extendRejectsEmptyOrUnanchoredSteps() {
        assertThatThrownBy(() -> expander.extend(List.of(), 24, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.PERIOD_EMPTY));

        assertThatThrownBy(() -> expander.extend(
                List.of(absolute(1, 2, 12, "42.8000")), 24, sevenPercent()))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(reason(RampReasons.GAP));
    }

    @Test
    @DisplayName("a missing mode or a non-positive interval reports RAMP_PERIOD_EMPTY")
    void incompleteEscalator() {
        RampEscalatorDto missing = escalator(RampEscalatorMode.PERCENT, "40", "5", 12);
        missing.setMode(null);
        assertThatThrownBy(() -> expander.expand(12, missing, 1))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.PERIOD_EMPTY));

        assertThatThrownBy(() -> expander.expand(12, escalator(RampEscalatorMode.AMOUNT, "40", "5", 0), 1))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.PERIOD_EMPTY));
    }

    private static List<String> prices(List<RampStepDto> steps) {
        return steps.stream().map(step -> step.getUnitPrice().toPlainString()).toList();
    }

    private static ThrowingConsumer<Throwable> reason(String expected) {
        return ex -> {
            RampScheduleException ramp = (RampScheduleException) ex;
            assertThat(ramp.getReasons()).containsExactly(expected);
            assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_INVALID);
        };
    }

    private static List<RampStepDto> goldenThirtySix() {
        return List.of(
                absolute(1, 1, 12, "42.8000"),
                absolute(2, 13, 12, "45.7960"),
                absolute(3, 25, 12, "49.0017"));
    }

    private static RampEscalatorDto sevenPercent() {
        return escalator(RampEscalatorMode.PERCENT, "42.80", "7", 12);
    }

    private static RampStepDto absolute(int sequence, int startMonth, int months, String price) {
        return RampStepDto.builder()
                .sequence(sequence)
                .startMonth(startMonth)
                .months(months)
                .quantity(2)
                .priceBasis(RampPriceBasis.ABSOLUTE)
                .unitPrice(new BigDecimal(price))
                .build();
    }

    private static RampEscalatorDto escalator(RampEscalatorMode mode, String start, String amount, int everyMonths) {
        return RampEscalatorDto.builder()
                .mode(mode)
                .startPrice(new BigDecimal(start))
                .amount(new BigDecimal(amount))
                .everyMonths(everyMonths)
                .build();
    }
}
