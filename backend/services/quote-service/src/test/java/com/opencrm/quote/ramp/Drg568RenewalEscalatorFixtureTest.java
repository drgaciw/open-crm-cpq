package com.opencrm.quote.ramp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flag-off fixture lock for DRG-568 ACs 568-1..568-7 against the landed DRG-532
 * generator and DRG-533 scale-4 arithmetic. Does not enable
 * {@code opencrm.pricing.ramp.enabled} and does not wire continuation.
 *
 * <p>Fixtures:
 * {@code docs/renewals-amendments/prereq/specs/fixtures/renewal-escalators/}.
 */
@DisplayName("DRG-568 renewal escalator / price-lock fixtures (flag off)")
class Drg568RenewalEscalatorFixtureTest {

    private final RampEscalatorExpander expander = new RampEscalatorExpander();

    @Test
    @DisplayName("AC-568-1: Y3 unit is 49.0017 and TCV is 330234.48 at 200 seats")
    void ac568_1_yearThreeIs490017AndTcv() {
        List<RampStepDto> steps = expander.expand(36, percent("42.80", "7", 12, true), 200);

        assertThat(prices(steps)).containsExactly("42.8000", "45.7960", "49.0017");
        assertThat(steps).extracting(RampStepDto::getMonths).containsExactly(12, 12, 12);
        assertThat(steps).extracting(RampStepDto::getStartMonth).containsExactly(1, 13, 25);

        BigDecimal tcv = BigDecimal.ZERO;
        for (RampStepDto step : steps) {
            BigDecimal monthly = step.getUnitPrice()
                    .multiply(BigDecimal.valueOf(200))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = monthly
                    .multiply(BigDecimal.valueOf(step.getMonths()))
                    .setScale(2, RoundingMode.HALF_UP);
            tcv = tcv.add(amount);
        }
        assertThat(tcv).isEqualByComparingTo("330234.48");
    }

    @Test
    @DisplayName("AC-568-2: post-lock unit is 21.4000 after one 7% uplift of locked 20.0000")
    void ac568_2_postLockAppliesPercentOnce() {
        BigDecimal locked = new BigDecimal("20.0000");
        BigDecimal postLock = locked
                .multiply(new BigDecimal("1.07"))
                .setScale(4, RoundingMode.HALF_UP);
        assertThat(postLock).isEqualByComparingTo("21.4000");

        BigDecimal lockAmount = locked.multiply(BigDecimal.valueOf(50))
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(5))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal afterAmount = postLock.multiply(BigDecimal.valueOf(50))
                .setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(9))
                .setScale(2, RoundingMode.HALF_UP);
        assertThat(lockAmount.add(afterAmount)).isEqualByComparingTo("14630.00");
    }

    @Test
    @DisplayName("AC-568-5: zero escalator keeps every period at period-1 unit")
    void ac568_5_zeroEscalatorStaysFlat() {
        List<RampStepDto> steps = expander.expand(36, percent("42.80", "0", 12, true), 1);
        assertThat(prices(steps)).containsExactly("42.8000", "42.8000", "42.8000");
    }

    @Test
    @DisplayName("AC-568-5: negative escalator amount is refused")
    void ac568_5_negativeAmountRefused() {
        assertThatThrownBy(() -> expander.expand(12, percent("42.80", "-7", 12, true), 1))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.NEGATIVE_VALUE));
    }

    @Test
    @DisplayName("AC-568-7: ramp flag property defaults off")
    void ac568_7_rampFlagDefaultsOff() {
        assertThat(new RampPricingProperties().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("AC-568-1 period-1 rule: startPrice is the uplift unit, not exit net 40")
    void ac568_1_period1IsUpliftUnitNotExitNet() {
        List<RampStepDto> fromExit = expander.expand(36, percent("40", "7", 12, true), 1);
        assertThat(prices(fromExit)).containsExactly("40.0000", "42.8000", "45.7960");

        List<RampStepDto> fromUplift = expander.expand(36, percent("42.80", "7", 12, true), 1);
        assertThat(prices(fromUplift)).containsExactly("42.8000", "45.7960", "49.0017");
    }

    private static List<String> prices(List<RampStepDto> steps) {
        return steps.stream().map(step -> step.getUnitPrice().toPlainString()).toList();
    }

    private static RampEscalatorDto percent(String start, String amount, int everyMonths, boolean compounding) {
        return RampEscalatorDto.builder()
                .mode(RampEscalatorMode.PERCENT)
                .startPrice(new BigDecimal(start))
                .amount(new BigDecimal(amount))
                .everyMonths(everyMonths)
                .compounding(compounding)
                .build();
    }
}
