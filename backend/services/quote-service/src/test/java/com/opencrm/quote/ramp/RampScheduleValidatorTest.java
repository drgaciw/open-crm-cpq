package com.opencrm.quote.ramp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Ramp schedule FR-02 checks")
class RampScheduleValidatorTest {

    private RampScheduleValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RampScheduleValidator();
    }

    @Test
    @DisplayName("gap between month 12 and month 14 reports RAMP_GAP")
    void gapReportsRampGap() {
        List<RampStepDto> steps = List.of(
                absolute(1, 1, 12, 1, "40"),
                absolute(2, 14, 23, 1, "42"));

        RampScheduleException ex = invalid(steps, 36, "MONTHLY", "SUBSCRIPTION");

        assertThat(ex.getReasons()).containsExactly(RampReasons.GAP);
        assertThat(ex.getMessage()).isEqualTo(RampReasons.GAP);
        assertThat(ex.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_INVALID);
        assertThat(ex.getStatus().value()).isEqualTo(422);
    }

    @Test
    @DisplayName("a schedule that does not start at month 1 reports only RAMP_GAP")
    void firstPeriodMustStartAtMonthOne() {
        RampScheduleException ex = invalid(
                List.of(absolute(1, 2, 12, 1, "40")),
                12,
                "MONTHLY",
                "SUBSCRIPTION");

        assertThat(ex.getReasons()).containsExactly(RampReasons.GAP);
    }

    @Test
    @DisplayName("overlapping periods that still sum to the term report only RAMP_OVERLAP")
    void overlapReportsOnlyOverlap() {
        List<RampStepDto> steps = List.of(
                absolute(1, 1, 20, 1, "40"),
                absolute(2, 13, 16, 1, "42"));

        RampScheduleException ex = invalid(steps, 36, "ANNUAL", "PRODUCT");

        assertThat(ex.getReasons()).containsExactly(RampReasons.OVERLAP);
    }

    @Test
    @DisplayName("contiguous periods that do not cover the term report only RAMP_TERM_MISMATCH")
    void termMismatchIsExclusive() {
        List<RampStepDto> steps = List.of(
                absolute(1, 1, 12, 1, "40"),
                absolute(2, 13, 18, 1, "42"));

        RampScheduleException ex = invalid(steps, 36, "QUARTERLY", "SERVICE");

        assertThat(ex.getReasons()).containsExactly(RampReasons.TERM_MISMATCH);
    }

    @Test
    @DisplayName("a zero-month period reports RAMP_PERIOD_EMPTY")
    void zeroMonthsReportsPeriodEmpty() {
        RampScheduleException ex = invalid(
                List.of(absolute(1, 1, 0, 1, "40")),
                12,
                "MONTHLY",
                "SUBSCRIPTION");

        assertThat(ex.getReasons()).containsExactly(RampReasons.PERIOD_EMPTY);
    }

    @Test
    @DisplayName("an empty schedule reports RAMP_PERIOD_EMPTY")
    void emptyScheduleReportsPeriodEmpty() {
        RampScheduleException ex = invalid(List.of(), 12, "MONTHLY", "SUBSCRIPTION");

        assertThat(ex.getReasons()).containsExactly(RampReasons.PERIOD_EMPTY);
    }

    @Test
    @DisplayName("a negative unit price reports RAMP_NEGATIVE_VALUE")
    void negativeUnitPrice() {
        RampScheduleException ex = invalid(
                List.of(absolute(1, 1, 12, 1, "-1")),
                12,
                "MONTHLY",
                "SUBSCRIPTION");

        assertThat(ex.getReasons()).containsExactly(RampReasons.NEGATIVE_VALUE);

        RampStepDto negativeQuantity = absolute(1, 1, 12, 1, "40");
        negativeQuantity.setQuantity(-1);
        assertThat(invalid(List.of(negativeQuantity), 12, "MONTHLY", "SUBSCRIPTION").getReasons())
                .containsExactly(RampReasons.NEGATIVE_VALUE);
    }

    @Test
    @DisplayName("a zero unit price, a zero quantity, and a full discount fraction are allowed")
    void zeroPriceAndFullFractionAreAllowed() {
        assertThatCode(() -> validator.validate(
                List.of(absolute(1, 1, 12, 0, "0")),
                12,
                "SEMI_ANNUAL",
                "SUBSCRIPTION",
                36)).doesNotThrowAnyException();

        assertThatCode(() -> validator.validate(
                List.of(fraction("1.0000")),
                12,
                "ANNUAL",
                "PRODUCT",
                36)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ONE_TIME, DISCOUNT, FEE, and TAX report only RAMP_NOT_ALLOWED_FOR_LINE_TYPE")
    void rejectedLinesShortCircuit() {
        List<RampStepDto> steps = List.of(absolute(1, 1, 12, 1, "40"));

        assertThat(invalid(steps, 12, "ONE_TIME", "SUBSCRIPTION").getReasons())
                .containsExactly(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE);
        assertThat(invalid(steps, 12, "MONTHLY", "DISCOUNT").getReasons())
                .containsExactly(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE);
        assertThat(invalid(steps, 12, "ANNUAL", "FEE").getReasons())
                .containsExactly(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE);
        assertThat(invalid(steps, 12, "BIENNIAL", "TAX").getReasons())
                .containsExactly(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE);
    }

    @Test
    @DisplayName("billable frequencies and product, service, and subscription lines pass")
    void allowedLinesPass() {
        List<RampStepDto> steps = List.of(absolute(1, 1, 12, 2, "40.0000"));
        for (String billing : List.of("monthly", "QUARTERLY", "SEMI_ANNUAL", "ANNUAL", "BIENNIAL")) {
            for (String type : new String[] {"PRODUCT", "SERVICE", "SUBSCRIPTION", null}) {
                assertThatCode(() -> validator.validate(steps, 12, billing, type, 36))
                        .doesNotThrowAnyException();
            }
        }
    }

    @Test
    @DisplayName("37 periods exceed the cap and 36 periods at the cap pass")
    void periodCapIs36() {
        assertThatThrownBy(() -> validator.validate(monthlySteps(37), 37, "MONTHLY", "SUBSCRIPTION", 36))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.TOO_MANY_PERIODS));

        assertThatCode(() -> validator.validate(monthlySteps(36), 36, "MONTHLY", "SUBSCRIPTION", 36))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a step needs exactly one price field, and discountFraction is a fraction")
    void priceBasisAndDiscountFraction() {
        RampStepDto both = absolute(1, 1, 12, 1, "40");
        both.setDiscountFraction(new BigDecimal("0.10"));
        assertThat(invalid(List.of(both), 12, "MONTHLY", "SUBSCRIPTION").getReasons())
                .containsExactly(RampReasons.PRICE_BASIS);

        RampStepDto missingQuantity = absolute(1, 1, 12, 1, "40");
        missingQuantity.setQuantity(null);
        assertThat(invalid(List.of(missingQuantity), 12, "MONTHLY", "SUBSCRIPTION").getReasons())
                .containsExactly(RampReasons.PERIOD_EMPTY);

        assertThatCode(() -> validator.validate(List.of(fraction("0.05")), 12, "ANNUAL", "PRODUCT", 36))
                .doesNotThrowAnyException();
        assertThat(invalid(List.of(fraction("5")), 12, "ANNUAL", "PRODUCT").getReasons())
                .containsExactly(RampReasons.PRICE_BASIS);
        assertThatCode(() -> validator.validate(List.of(fraction("0.2500")), 12, "ANNUAL", "PRODUCT", 36))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("duplicate sequences are not a schedule reason")
    void duplicateSequenceIsNotAReason() {
        List<RampStepDto> steps = List.of(
                absolute(1, 1, 6, 1, "40"),
                absolute(1, 7, 6, 1, "42"));

        assertThatCode(() -> validator.validate(steps, 12, "MONTHLY", "SUBSCRIPTION", 36))
                .doesNotThrowAnyException();
    }

    private RampScheduleException invalid(List<RampStepDto> steps, int duration, String billing, String type) {
        assertThatThrownBy(() -> validator.validate(steps, duration, billing, type, 36))
                .isInstanceOf(RampScheduleException.class);
        try {
            validator.validate(steps, duration, billing, type, 36);
            throw new AssertionError("expected RampScheduleException");
        } catch (RampScheduleException ex) {
            return ex;
        }
    }

    private static List<RampStepDto> monthlySteps(int count) {
        List<RampStepDto> steps = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            steps.add(absolute(i + 1, i + 1, 1, 1, "10"));
        }
        return steps;
    }

    private static RampStepDto fraction(String discountFraction) {
        return RampStepDto.builder()
                .sequence(1)
                .startMonth(1)
                .months(12)
                .quantity(1)
                .priceBasis(RampPriceBasis.DISCOUNT_FRACTION)
                .discountFraction(new BigDecimal(discountFraction))
                .build();
    }

    private static RampStepDto absolute(int sequence, int startMonth, int months, int quantity, String unitPrice) {
        return RampStepDto.builder()
                .sequence(sequence)
                .startMonth(startMonth)
                .months(months)
                .quantity(quantity)
                .priceBasis(RampPriceBasis.ABSOLUTE)
                .unitPrice(new BigDecimal(unitPrice))
                .build();
    }
}
