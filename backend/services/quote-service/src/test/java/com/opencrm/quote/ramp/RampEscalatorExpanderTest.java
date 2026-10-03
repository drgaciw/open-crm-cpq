package com.opencrm.quote.ramp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ramp escalator AC-04")
class RampEscalatorExpanderTest {

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
    @DisplayName("the last period keeps the remainder months")
    void lastPeriodTakesRemainder() {
        List<RampStepDto> steps = expander.expand(14, escalator(RampEscalatorMode.PERCENT, "40", "5", 12), 1);

        assertThat(steps).extracting(RampStepDto::getMonths).containsExactly(12, 2);
        assertThat(steps).extracting(RampStepDto::getStartMonth).containsExactly(1, 13);
        assertThat(steps.stream().mapToInt(RampStepDto::getMonths).sum()).isEqualTo(14);
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
    @DisplayName("a missing mode or a non-positive interval reports RAMP_ESCALATOR_INVALID")
    void invalidEscalator() {
        RampEscalatorDto missing = escalator(RampEscalatorMode.PERCENT, "40", "5", 12);
        missing.setMode(null);
        assertThatThrownBy(() -> expander.expand(12, missing, 1))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.ESCALATOR_INVALID));

        assertThatThrownBy(() -> expander.expand(12, escalator(RampEscalatorMode.AMOUNT, "40", "5", 0), 1))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.ESCALATOR_INVALID));
    }

    private static List<String> prices(List<RampStepDto> steps) {
        return steps.stream().map(step -> step.getUnitPrice().toPlainString()).toList();
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
