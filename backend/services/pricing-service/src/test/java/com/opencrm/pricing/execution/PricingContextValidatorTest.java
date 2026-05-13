package com.opencrm.pricing.execution;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingLineRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Pricing Context Validator")
class PricingContextValidatorTest {

    private final PricingContextValidator validator = new PricingContextValidator();

    @Test
    @DisplayName("validate() accepts a complete pricing context")
    void validateAcceptsCompletePricingContext() {
        assertDoesNotThrow(() -> validator.validate(validContext()));
    }

    @Test
    @DisplayName("validate() rejects blank customer IDs")
    void validateRejectsBlankCustomerIds() {
        PricingContext context = validContext();
        context.setCustomerExternalId("  ");

        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(context));

        assertEquals("INVALID_CONTEXT", ex.getErrorCode());
        assertEquals("Customer external ID is required", ex.getMessage());
    }

    @Test
    @DisplayName("validate() rejects missing line items")
    void validateRejectsMissingLineItems() {
        PricingContext context = validContext();
        context.setLineItems(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(context));

        assertEquals("INVALID_CONTEXT", ex.getErrorCode());
        assertEquals("At least one line item is required", ex.getMessage());
    }

    @Test
    @DisplayName("validate() rejects blank product IDs")
    void validateRejectsBlankProductIds() {
        PricingContext context = validContext();
        context.getLineItems().get(0).setProductExternalId("");

        BusinessException ex = assertThrows(BusinessException.class, () -> validator.validate(context));

        assertEquals("INVALID_CONTEXT", ex.getErrorCode());
        assertEquals("Product external ID is required for all line items", ex.getMessage());
    }

    private static PricingContext validContext() {
        return PricingContext.builder()
                .customerExternalId("CUST-001")
                .durationInMonths(12)
                .lineItems(List.of(PricingLineRequest.builder()
                        .productExternalId("PROD-001")
                        .quantity(1)
                        .build()))
                .build();
    }
}
