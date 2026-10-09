package com.opencrm.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class DiscountApprovalRequiredEventToleranceTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void preRampPayloadStillParsesWithAbsentBindingFields() throws Exception {
        var event = json.readValue("{\"quoteId\":\"Q-1\",\"lineId\":\"L-1\",\"marginDelta\":25,\"requestedDiscount\":18.5,\"currencyCode\":\"USD\"}",
                DiscountApprovalRequiredEvent.class);
        assertThat(event.getMarginDelta()).isEqualByComparingTo("25");
        assertThat(event.getPricingFingerprint()).isNull();
        assertThat(event.getApprovalRequired()).isNull();
    }
    @Test void capturedPreChangeConsumerIgnoresAdditiveFieldsAndLegacyTopicOwnerStaysPricing() throws Exception {
        var current = new DiscountApprovalRequiredEvent("1", "10", BigDecimal.ZERO, new BigDecimal("0.32"), "USD",
                "a".repeat(64), 1, "SALES_VP", "Ramp period 1", true);
        var legacy = json.readValue(json.writeValueAsString(current), LegacyDiscount.class);
        assertThat(legacy.quoteId()).isEqualTo("1");
        assertThat(legacy.requestedDiscount()).isEqualByComparingTo("0.32");
        assertThat(EventContractRegistry.DISCOUNT_APPROVAL_REQUIRED_V1.owner()).isEqualTo("pricing-service");
        assertThat(EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1.owner()).isEqualTo("quote-service");
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LegacyDiscount(String quoteId, String lineId, BigDecimal marginDelta, BigDecimal requestedDiscount, String currencyCode) {}
}
