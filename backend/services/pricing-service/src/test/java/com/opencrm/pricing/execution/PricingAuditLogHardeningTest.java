package com.opencrm.pricing.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Pricing Audit Log Hardening")
class PricingAuditLogHardeningTest {

    @Test
    @DisplayName("PriceCalculation record maps audit metadata fields")
    void priceCalculationRecordMapsAuditMetadata() {
        PriceCalculation calculation = new PriceCalculation(
                "corr-1",
                "Q-1",
                "C-1",
                "pb-3",
                "rs-9",
                null,
                null,
                null,
                42L
        );

        assertNotNull(calculation.correlationId());
        assertNotNull(calculation.pricebookVersion());
        assertNotNull(calculation.rulesetVersion());
    }
}
