package com.opencrm.pricing.execution;

import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parity vectors for hybrid §5.1a metric derivation.
 *
 * <p>SPEC source: pricing-ai-rules-service {@code PricingRules.drl} Calculate ARR/TCV/ACV/CLV
 * and {@code FinancialMetricsServiceImpl} @ pin {@code 3aa45bf} — do not port those rules as DRL.
 */
@Tag("unit")
@DisplayName("Metrics Calculator")
class MetricsCalculatorTest {

    private final MetricsCalculator calculator = new MetricsCalculator();

    @Test
    @DisplayName("should calculate required quote financial metrics (duration×2 CLV proxy)")
    void shouldCalculateRequiredQuoteFinancialMetrics() {
        PricingResult result = PricingResult.builder()
                .lineResults(List.of(
                        PricingLineResult.builder()
                                .quantity(2)
                                .listPrice(BigDecimal.valueOf(100))
                                .finalPrice(BigDecimal.valueOf(180))
                                .discountAmount(BigDecimal.valueOf(20))
                                .surchargeAmount(BigDecimal.ZERO)
                                .build(),
                        PricingLineResult.builder()
                                .quantity(1)
                                .listPrice(BigDecimal.valueOf(50))
                                .finalPrice(BigDecimal.valueOf(45))
                                .discountAmount(BigDecimal.valueOf(5))
                                .surchargeAmount(BigDecimal.ZERO)
                                .build()))
                .build();
        Customer customer = Customer.builder()
                .churnScore(68)
                .build();

        FinancialMetrics metrics = calculator.calculate(result, 24, customer);

        // monthlyPrice = 180 + 45 = 225
        assertThat(metrics.getArr()).isEqualByComparingTo("2700.00");
        assertThat(metrics.getTcv()).isEqualByComparingTo("5400.00");
        assertThat(metrics.getAcv()).isEqualByComparingTo("450.00");
        assertThat(metrics.getClv()).isEqualByComparingTo("10800.00");
        assertThat(metrics.getChurnScore()).isEqualTo(68);
        assertThat(metrics.getDiscountRate()).isEqualByComparingTo("0.1000");
        assertThat(metrics.getTotalDiscount()).isEqualByComparingTo("25");
        assertThat(metrics.getPriceMultiplier()).isEqualByComparingTo("0.9000");
    }

    @Test
    @DisplayName("should return zeros when result has no lines")
    void emptyResultZeros() {
        FinancialMetrics empty = calculator.calculate(null, 12);
        assertThat(empty.getArr()).isEqualByComparingTo(BigDecimal.ZERO);
        Customer customer = Customer.builder().churnScore(12).build();
        FinancialMetrics withCustomer = calculator.calculate(
                PricingResult.builder().lineResults(List.of()).build(), 0, customer);
        assertThat(withCustomer.getChurnScore()).isEqualTo(12);
        assertThat(withCustomer.getTcv()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should treat null line amounts as zero")
    void nullLineAmounts() {
        PricingResult result = PricingResult.builder()
                .lineResults(List.of(PricingLineResult.builder().quantity(null).build()))
                .build();
        FinancialMetrics metrics = calculator.calculate(result, 0);
        assertThat(metrics.getArr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.getDiscountRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Nested
    @DisplayName("Source Calculate-* SPEC vectors (3aa45bf)")
    class SourceCalculateSpecVectors {

        @Test
        @DisplayName("Calculate ARR: monthlyPrice × 12")
        void calculateArr_matchesSource() {
            // FinancialMetricsServiceImplTest.calculateARR_withMonthlyPrice_returnsAnnualized
            FinancialMetrics metrics = calculator.calculate(singleLineMonthly("1000.00"), 12);

            assertThat(metrics.getArr()).isEqualByComparingTo("12000.00");
        }

        @Test
        @DisplayName("Calculate TCV: monthlyPrice × durationInMonths")
        void calculateTcv_matchesSource() {
            // FinancialMetricsServiceImplTest.calculateTCV_withMonthlyPriceAndDuration_returnsProduct
            FinancialMetrics metrics = calculator.calculate(singleLineMonthly("500.00"), 24);

            assertThat(metrics.getTcv()).isEqualByComparingTo("12000.00");
        }

        @Test
        @DisplayName("Calculate ACV: (monthlyPrice × duration) / 12")
        void calculateAcv_matchesSource() {
            // FinancialMetricsServiceImplTest.calculateACV_withMonthlyPriceAndDuration_dividesBy12
            FinancialMetrics metrics = calculator.calculate(singleLineMonthly("1000.00"), 24);

            assertThat(metrics.getAcv()).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("Calculate CLV: monthlyPrice × expectedDuration when provided")
        void calculateClv_withExpectedDuration_matchesSource() {
            // FinancialMetricsServiceImplTest.calculateCLV_withMonthlyPriceAndExpectedDuration_returnsProduct
            FinancialMetrics metrics = calculator.calculate(
                    singleLineMonthly("200.00"), 12, null, 36);

            assertThat(metrics.getClv()).isEqualByComparingTo("7200.00");
        }

        @Test
        @DisplayName("Calculate CLV: duration × 2 proxy when expectedDuration unset")
        void calculateClv_usesDurationTimesTwoProxy() {
            FinancialMetrics metrics = calculator.calculate(singleLineMonthly("100.00"), 12);

            assertThat(metrics.getClv()).isEqualByComparingTo("2400.00");
        }

        @Test
        @DisplayName("combined Calculate-* vector (source calculateFinancialMetrics_setsAllMetricsOnQuote)")
        void combinedSourceVector() {
            FinancialMetrics metrics = calculator.calculate(
                    singleLineMonthly("100.00"), 12, null, 24);

            assertThat(metrics.getArr()).isEqualByComparingTo("1200.00");
            assertThat(metrics.getTcv()).isEqualByComparingTo("1200.00");
            assertThat(metrics.getAcv()).isEqualByComparingTo("100.00");
            assertThat(metrics.getClv()).isEqualByComparingTo("2400.00");
        }

        @Test
        @DisplayName("evaluate golden shape: 12-mo list-price monthly basis → ARR=TCV")
        void evaluateMappedHappyPathMetricsShape() {
            // Adapted from pricing-evaluate-001 → execute/04 (no enterprise discount): monthly 12000
            FinancialMetrics metrics = calculator.calculate(singleLineMonthly("12000.00"), 12);

            assertThat(metrics.getArr()).isEqualByComparingTo("144000.00");
            assertThat(metrics.getTcv()).isEqualByComparingTo("144000.00");
            assertThat(metrics.getAcv()).isEqualByComparingTo("12000.00");
            assertThat(metrics.getClv()).isEqualByComparingTo("288000.00");
        }
    }

    private static PricingResult singleLineMonthly(String monthlyFinalPrice) {
        return PricingResult.builder()
                .lineResults(List.of(PricingLineResult.builder()
                        .quantity(1)
                        .listPrice(new BigDecimal(monthlyFinalPrice))
                        .finalPrice(new BigDecimal(monthlyFinalPrice))
                        .discountAmount(BigDecimal.ZERO)
                        .surchargeAmount(BigDecimal.ZERO)
                        .build()))
                .build();
    }
}
