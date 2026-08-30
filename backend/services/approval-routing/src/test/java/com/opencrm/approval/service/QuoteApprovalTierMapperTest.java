package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QuoteApprovalTierMapper")
class QuoteApprovalTierMapperTest {

    @ParameterizedTest(name = "{0} → tier={1}, type={2}")
    @CsvSource({
            "DEAL_DESK, DEAL_DESK, QUOTE",
            "SALES_VP, SALES_VP, QUOTE",
            "CFO, CFO, GOVERNANCE"
    })
    void mapsQuoteTierToRoutingTierAndTypes(QuoteApprovalTier input, String expectedTier, ApprovalType expectedType) {
        QuoteApprovalTierMapper.MappedApproval mapped = QuoteApprovalTierMapper.map(input);

        assertEquals(expectedTier, mapped.approvalTier());
        assertEquals(expectedType, mapped.approvalType());
    }

    @Test
    @DisplayName("null tier → BusinessException")
    void rejectsNullTier() {
        BusinessException ex = assertThrows(BusinessException.class, () -> QuoteApprovalTierMapper.map(null));
        assertEquals("INVALID_APPROVAL_TIER", ex.getErrorCode());
    }
}
