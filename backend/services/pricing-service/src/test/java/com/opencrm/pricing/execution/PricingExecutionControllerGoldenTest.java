package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract goldens for {@code POST /api/v1/pricing/execute} (matrix §4 / S2C-1b).
 * Standalone MockMvc — Docker-free, no Spring Boot context.
 */
@Tag("unit")
@DisplayName("PricingExecutionController execute goldens")
class PricingExecutionControllerGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private PricingExecutionService pricingExecutionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pricingExecutionService = mock(PricingExecutionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new PricingExecutionController(pricingExecutionService))
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/fixtures/pricing/execute/01-list-price-baseline.json",
            "/fixtures/pricing/execute/02-multi-line-override.json",
            "/fixtures/pricing/execute/03-override-only.json"
    })
    @DisplayName("POST /api/v1/pricing/execute wraps golden PricingResult in ApiResponse")
    void executeEndpointReturnsApiResponse(String fixturePath) throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getResourceAsStream(fixturePath)) {
            assertThat(in).as("fixture %s", fixturePath).isNotNull();
            root = MAPPER.readTree(in);
        }

        PricingContext request = MAPPER.treeToValue(root.get("request"), PricingContext.class);
        PricingResult golden = MAPPER.treeToValue(root.get("expected"), PricingResult.class);
        golden.setExecutionTimeMs(1L);

        when(pricingExecutionService.execute(any(PricingContext.class))).thenReturn(golden);

        mockMvc.perform(post("/api/v1/pricing/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Pricing executed successfully"))
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.approvalState").value(golden.getApprovalState()))
                .andExpect(jsonPath("$.data.waterfall.approvalState").value(golden.getWaterfall().getApprovalState()))
                .andExpect(jsonPath("$.data.financialMetrics.estimatedMarginPct")
                        .value(golden.getFinancialMetrics().getEstimatedMarginPct().doubleValue()))
                .andExpect(jsonPath("$.data.lineResults.length()").value(golden.getLineResults().size()));
    }
}
