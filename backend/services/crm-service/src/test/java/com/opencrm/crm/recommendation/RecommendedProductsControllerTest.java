package com.opencrm.crm.recommendation;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendedProductsController")
class RecommendedProductsControllerTest {

    @Mock
    private RecommendedProductsService recommendedProductsService;

    @InjectMocks
    private RecommendedProductsController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("returns product id, rationale, and confidence")
    void returnsAdvisoryItems() throws Exception {
        when(recommendedProductsService.recommend(3L)).thenReturn(List.of(
                new RecommendedProductDto("addon-analytics", "Advisory suggestion for Analytics add-on.", new BigDecimal("0.80")),
                new RecommendedProductDto("services-onboarding", "Advisory suggestion for Onboarding services.", new BigDecimal("0.65"))));

        mockMvc.perform(get("/api/v1/deals/3/recommended-products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].productId").value("addon-analytics"))
                .andExpect(jsonPath("$.data[0].rationale").value("Advisory suggestion for Analytics add-on."))
                .andExpect(jsonPath("$.data[0].confidence").value(0.8))
                .andExpect(jsonPath("$.data[1].productId").value("services-onboarding"))
                .andExpect(jsonPath("$.data[1].rationale").isNotEmpty());
    }

    @Test
    @DisplayName("a missing deal is 404")
    void missingDealIsNotFound() throws Exception {
        when(recommendedProductsService.recommend(9L)).thenThrow(new ResourceNotFoundException("Deal", 9L));

        mockMvc.perform(get("/api/v1/deals/9/recommended-products"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
