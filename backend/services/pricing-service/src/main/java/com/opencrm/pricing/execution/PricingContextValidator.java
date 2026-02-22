package com.opencrm.pricing.execution;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.waterfall.PricingContext;
import org.springframework.stereotype.Component;

/**
 * Validator for pricing context.
 */
@Component
public class PricingContextValidator {

    /**
     * Validate pricing context before execution.
     */
    public void validate(PricingContext context) {
        if (context == null) {
            throw new BusinessException("Pricing context cannot be null", "INVALID_CONTEXT");
        }

        if (context.getCustomerExternalId() == null || context.getCustomerExternalId().isBlank()) {
            throw new BusinessException("Customer external ID is required", "INVALID_CONTEXT");
        }

        if (context.getLineItems() == null || context.getLineItems().isEmpty()) {
            throw new BusinessException("At least one line item is required", "INVALID_CONTEXT");
        }

        if (context.getDurationInMonths() == null || context.getDurationInMonths() <= 0) {
            throw new BusinessException("Duration in months must be positive", "INVALID_CONTEXT");
        }

        // Validate each line item
        context.getLineItems().forEach(lineItem -> {
            if (lineItem.getProductExternalId() == null || lineItem.getProductExternalId().isBlank()) {
                throw new BusinessException("Product external ID is required for all line items", "INVALID_CONTEXT");
            }

            if (lineItem.getQuantity() == null || lineItem.getQuantity() <= 0) {
                throw new BusinessException("Quantity must be positive for all line items", "INVALID_CONTEXT");
            }
        });
    }
}
