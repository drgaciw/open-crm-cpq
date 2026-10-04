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
    @DisplayName("typed priced periods and exitArr bind through a plain ObjectMapper")
    void bindsOptionalRampFields() throws Exception {
        String json = """
                {
                  "success": true,
                  "data": {
                    "success": true,
                    "lineResults": [{
                      "productExternalId": "PROD-1",
                      "lineItemType": "SUBSCRIPTION",
                      "pricedPeriods": [{
                        "sequence": 1,
                        "startMonth": 1,
                        "months": 12,
                        "quantity": 50,
                        "netUnitPrice": 37.2000,
                        "netMonthly": 1860.00,
                        "periodAmount": 22320.00,
                        "discountRate": 0.07,
                        "explanationCodes": ["NAMED_SEAT"],
                        "waterfall": [{"code": "LIST_PRICE"}]
                      }]
                    }],
                    "financialMetrics": {
                      "acv": 450.00,
                      "exitArr": 99.00
                    }
                  }
                }
                """;

        PricingExecuteView view = objectMapper.readValue(json, responseType).getData();
        PricedPeriodView period = view.getLineResults().getFirst().getPricedPeriods().getFirst();

        assertThat(period.getSequence()).isEqualTo(1);
        assertThat(period.getStartMonth()).isEqualTo(1);
        assertThat(period.getMonths()).isEqualTo(12);
        assertThat(period.getQuantity()).isEqualTo(50);
        assertThat(period.getNetUnitPrice()).isEqualByComparingTo(new BigDecimal("37.2000"));
        assertThat(period.getNetMonthly()).isEqualByComparingTo(new BigDecimal("1860.00"));
        assertThat(period.getPeriodAmount()).isEqualByComparingTo(new BigDecimal("22320.00"));
        assertThat(period.getDiscountRate()).isEqualByComparingTo(new BigDecimal("0.07"));
        assertThat(period.getExplanationCodes()).containsExactly("NAMED_SEAT");
        assertThat(period.getWaterfall().isArray()).isTrue();
        assertThat(period.getWaterfall().get(0).get("code").asText()).isEqualTo("LIST_PRICE");
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

        assertThat(view.getLineResults().getFirst().getPricedPeriods()).isNull();
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

    @Test
    @DisplayName("execute view rejects pricedPeriods and exitArr, and a period rejects discountFraction")
    void closedFieldListsRejectExtras() {
        String rootPeriods = """
                {"success": true, "data": {"success": true, "pricedPeriods": []}}
                """;
        String rootExit = """
                {"success": true, "data": {"success": true, "exitArr": 1}}
                """;
        String periodFraction = """
                {
                  "success": true,
                  "data": {
                    "success": true,
                    "lineResults": [{
                      "pricedPeriods": [{"sequence": 1, "discountFraction": 0.05}]
                    }]
                  }
                }
                """;

        assertThatThrownBy(() -> objectMapper.readValue(rootPeriods, responseType))
                .isInstanceOf(UnrecognizedPropertyException.class);
        assertThatThrownBy(() -> objectMapper.readValue(rootExit, responseType))
                .isInstanceOf(UnrecognizedPropertyException.class);
        assertThatThrownBy(() -> objectMapper.readValue(periodFraction, responseType))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }
}
