package com.opencrm.pricing.optimization;

import com.opencrm.pricing.execution.PricingExecutionService;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineResult;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceOptimizationServiceTest {

    @Mock
    private PricingExecutionService pricingExecutionService;

    @InjectMocks
    private PriceOptimizationService priceOptimizationService;

    @Test
    void optimizeReturnsAdvisoryElementsWithModelVersionInTrace() {
        PricingLineResult line = PricingLineResult.builder()
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("100.00"))
                .unitPrice(new BigDecimal("90.00"))
                .finalPrice(new BigDecimal("90.00"))
                .explanationCodes(List.of("LIST"))
                .build();
        when(pricingExecutionService.simulate(any())).thenReturn(PricingResult.builder()
                .success(true)
                .lineResults(List.of(line))
                .build());

        PriceOptimizationResponse response = priceOptimizationService.optimize(
                PricingContext.builder().quoteExternalId("Q-1").build());

        assertThat(response.isAdvisory()).isTrue();
        assertThat(response.getModelVersion()).isEqualTo(PriceOptimizationService.MODEL_VERSION);
        assertThat(response.getRecommendations()).hasSize(1);
        AdvisoryPriceElement element = response.getRecommendations().getFirst();
        assertThat(element.isAdvisory()).isTrue();
        assertThat(element.getRecommendedUnitPrice()).isEqualByComparingTo("95.00");
        assertThat(element.getTrace()).containsEntry("modelVersion", PriceOptimizationService.MODEL_VERSION);
        assertThat(element.getGuidanceFloor()).isLessThanOrEqualTo(element.getRecommendedUnitPrice());
        assertThat(element.getGuidanceCeiling()).isGreaterThanOrEqualTo(element.getRecommendedUnitPrice());
    }
}
