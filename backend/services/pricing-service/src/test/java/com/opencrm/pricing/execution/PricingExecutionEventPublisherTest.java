package com.opencrm.pricing.execution;

import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.pricing.waterfall.FinancialMetrics;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pricing Execution Event Publisher")
class PricingExecutionEventPublisherTest {

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private PricingExecutionEventPublisher publisher;

    @Test
    @DisplayName("publishes PriceCalculated for every execution")
    void publishesPriceCalculated() {
        PricingContext context = PricingContext.builder()
                .quoteExternalId("Q-100")
                .currency("USD")
                .build();
        PricingResult result = PricingResult.builder()
                .success(true)
                .approvalState("APPROVED")
                .lineResults(List.of(PricingLineResult.builder()
                        .productExternalId("SKU-1")
                        .quantity(1)
                        .listPrice(BigDecimal.TEN)
                        .finalPrice(BigDecimal.TEN)
                        .build()))
                .financialMetrics(FinancialMetrics.builder()
                        .tcv(BigDecimal.TEN)
                        .discountRate(BigDecimal.valueOf(0.05))
                        .totalDiscount(BigDecimal.ONE)
                        .build())
                .build();

        publisher.publishOutcomes(context, result, "pb-1", "rs-2");

        verify(outboxEventPublisher).publish(
                eq("PriceCalculation"),
                eq("Q-100"),
                eq(KafkaTopics.PRICE_CALCULATED),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("publishes DiscountApprovalRequired when discount rate exceeds threshold")
    void publishesDiscountApprovalWhenRequired() {
        PricingContext context = PricingContext.builder()
                .quoteExternalId("Q-200")
                .currency("USD")
                .build();
        PricingResult result = PricingResult.builder()
                .success(true)
                .lineResults(List.of(PricingLineResult.builder()
                        .productExternalId("SKU-2")
                        .quantity(1)
                        .listPrice(BigDecimal.valueOf(100))
                        .finalPrice(BigDecimal.valueOf(70))
                        .build()))
                .financialMetrics(FinancialMetrics.builder()
                        .tcv(BigDecimal.valueOf(70))
                        .discountRate(new BigDecimal("0.30"))
                        .totalDiscount(BigDecimal.valueOf(30))
                        .build())
                .build();

        publisher.publishOutcomes(context, result, "pb-9", null);

        verify(outboxEventPublisher, times(2)).publish(
                eq("PriceCalculation"),
                eq("Q-200"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
        verify(outboxEventPublisher).publish(
                eq("PriceCalculation"),
                eq("Q-200"),
                eq(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
