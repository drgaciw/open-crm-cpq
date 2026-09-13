package com.opencrm.pricing.execution;

import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Metrics Calculator")
class MetricsCalculatorTest {

    private final MetricsCalculator calculator = new MetricsCalculator();

    @Test
    @DisplayName("should calculate required quote financial metrics")
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

        assertEquals(BigDecimal.valueOf(2700), metrics.getArr());
        assertEquals(BigDecimal.valueOf(5400), metrics.getTcv());
        assertEquals(new BigDecimal("450.00"), metrics.getAcv());
        assertEquals(BigDecimal.valueOf(10800), metrics.getClv());
        assertEquals(68, metrics.getChurnScore());
        assertEquals(new BigDecimal("0.1000"), metrics.getDiscountRate());
        assertEquals(BigDecimal.valueOf(25), metrics.getTotalDiscount());
        assertEquals(new BigDecimal("0.9000"), metrics.getPriceMultiplier());
    }

    @Test
    @DisplayName("should return zeros when result has no lines")
    void emptyResultZeros() {
        FinancialMetrics empty = calculator.calculate(null, 12);
        assertEquals(BigDecimal.ZERO, empty.getArr());
        Customer customer = Customer.builder().churnScore(12).build();
        FinancialMetrics withCustomer = calculator.calculate(
                PricingResult.builder().lineResults(List.of()).build(), 0, customer);
        assertEquals(12, withCustomer.getChurnScore());
        assertEquals(BigDecimal.ZERO, withCustomer.getTcv());
    }

    @Test
    @DisplayName("should treat null line amounts as zero")
    void nullLineAmounts() {
        PricingResult result = PricingResult.builder()
                .lineResults(List.of(PricingLineResult.builder().quantity(null).build()))
                .build();
        FinancialMetrics metrics = calculator.calculate(result, 0);
        assertEquals(BigDecimal.ZERO, metrics.getArr());
        assertEquals(BigDecimal.ZERO, metrics.getDiscountRate());
    }
}
