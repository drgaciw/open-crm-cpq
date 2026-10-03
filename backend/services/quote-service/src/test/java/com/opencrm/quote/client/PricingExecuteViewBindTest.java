package com.opencrm.quote.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.opencrm.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pricing execute view optional ramp fields")
class PricingExecuteViewBindTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TypeReference<ApiResponse<PricingExecuteView>> responseType = new TypeReference<>() {
    };

    @Test
    @DisplayName("pricedPeriods and exitArr bind through a plain ObjectMapper")
    void bindsOptionalRampFields() throws Exception {
        String json = """
                {
                  "success": true,
                  "data": {
                    "success": true,
                    "pricedPeriods": [{"sequence": 1, "amount": "40.0000"}],
                    "exitArr": 120.50,
                    "lineResults": [{
                      "productExternalId": "PROD-1",
                      "lineItemType": "SUBSCRIPTION",
                      "pricedPeriods": {"months": 12},
                      "exitArr": 10.00
                    }],
                    "financialMetrics": {
                      "acv": 450.00,
                      "pricedPeriods": [1, 2],
                      "exitArr": 99.00
                    }
                  }
                }
                """;

        ApiResponse<PricingExecuteView> response = objectMapper.readValue(json, responseType);
        PricingExecuteView view = response.getData();

        assertThat(view.getPricedPeriods().isArray()).isTrue();
        assertThat(view.getExitArr()).isEqualByComparingTo(new BigDecimal("120.50"));
        assertThat(view.getLineResults().getFirst().getPricedPeriods().get("months").asInt()).isEqualTo(12);
        assertThat(view.getLineResults().getFirst().getExitArr()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(view.getFinancialMetrics().getPricedPeriods().isArray()).isTrue();
        assertThat(view.getFinancialMetrics().getExitArr()).isEqualByComparingTo(new BigDecimal("99.00"));
        assertThat(view.getFinancialMetrics().getAcv()).isEqualByComparingTo(new BigDecimal("450.00"));
    }

    @Test
    @DisplayName("absent pricedPeriods and exitArr stay null")
    void absentFieldsStayNull() throws Exception {
        String json = """
                {
                  "success": true,
                  "data": {
                    "success": true,
                    "lineResults": [{"productExternalId": "PROD-1"}],
                    "financialMetrics": {"acv": 1}
                  }
                }
                """;

        PricingExecuteView view = objectMapper.readValue(json, responseType).getData();

        assertThat(view.getPricedPeriods()).isNull();
        assertThat(view.getExitArr()).isNull();
        assertThat(view.getLineResults().getFirst().getPricedPeriods()).isNull();
        assertThat(view.getLineResults().getFirst().getExitArr()).isNull();
        assertThat(view.getFinancialMetrics().getPricedPeriods()).isNull();
        assertThat(view.getFinancialMetrics().getExitArr()).isNull();
    }

    @Test
    @DisplayName("unknown pricing fields still fail the plain ObjectMapper")
    void unknownPropertyStillFails() {
        String json = """
                {"success": true, "data": {"success": true, "notAPricingField": 1}}
                """;

        assertThatThrownBy(() -> objectMapper.readValue(json, responseType))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
