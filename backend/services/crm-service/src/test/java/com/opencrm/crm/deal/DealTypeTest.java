package com.opencrm.crm.deal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the CRM catalog to the keys of quote-service {@code CrmQuoteDealTypeMapping}.
 * Adding a constant here without a mapping row makes the handoff reject that name.
 */
class DealTypeTest {

    @Test
    @DisplayName("CRM DealType names are exactly the opportunity-to-quote mapping keys")
    void catalogMatchesTheQuoteHandoffTable() {
        assertThat(DealType.values())
                .extracting(Enum::name)
                .containsExactlyInAnyOrder(
                        "NEW_BUSINESS",
                        "EXISTING_BUSINESS",
                        "RENEWAL",
                        "UPSELL",
                        "CROSS_SELL");
    }
}
