package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CRM DealType to quote DealType handoff table")
class CrmQuoteDealTypeMappingTest {

    @Test
    @DisplayName("the table is exactly the CRM catalog, with a quote target for every row")
    void tableIsTotalOverTheCrmCatalog() {
        assertThat(CrmQuoteDealTypeMapping.crmNames()).containsExactlyInAnyOrder(
                "NEW_BUSINESS",
                "EXISTING_BUSINESS",
                "RENEWAL",
                "UPSELL",
                "CROSS_SELL");
        assertThat(DealType.values()).extracting(Enum::name)
                .containsExactly("NEW", "AMENDMENT", "RENEWAL", "EXPANSION");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "NEW_BUSINESS, NEW",
            "EXISTING_BUSINESS, AMENDMENT",
            "RENEWAL, RENEWAL",
            "UPSELL, EXPANSION",
            "CROSS_SELL, EXPANSION"
    })
    @DisplayName("each CRM value maps to the documented quote deal type")
    void mapsEveryCrmValue(String crmDealType, DealType expected) {
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType(crmDealType)).isEqualTo(expected);
    }

    @Test
    @DisplayName("pricing rules see the mapped name: RENEWAL for uplift, AMENDMENT for proration, EXPANSION for neither")
    void mappedNamesAreThePricingRuleKeys() {
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("RENEWAL").name()).isEqualTo("RENEWAL");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("EXISTING_BUSINESS").name()).isEqualTo("AMENDMENT");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("UPSELL").name()).isEqualTo("EXPANSION");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("CROSS_SELL").name()).isEqualTo("EXPANSION");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("NEW_BUSINESS").name()).isEqualTo("NEW");
        assertThat(Set.of("RENEWAL", "AMENDMENT")).doesNotContain(
                CrmQuoteDealTypeMapping.toQuoteDealType("UPSELL").name(),
                CrmQuoteDealTypeMapping.toQuoteDealType("CROSS_SELL").name());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "renewal, RENEWAL",
            "' Renewal ', RENEWAL",
            "upsell, EXPANSION",
            "cross_sell, EXPANSION",
            "new_business, NEW",
            "existing_business, AMENDMENT"
    })
    @DisplayName("matching ignores case and surrounding whitespace")
    void normalizesCaseAndWhitespace(String crmDealType, DealType expected) {
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType(crmDealType)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "  "})
    @DisplayName("null and blank fail; they are not the NEW default")
    void nullAndBlankFail(String crmDealType) {
        assertThatThrownBy(() -> CrmQuoteDealTypeMapping.toQuoteDealType(crmDealType))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CRM_DEAL_TYPE_REQUIRED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXTENSION", "NEW", "AMENDMENT", "EXPANSION", "FOO", "NEW BUSINESS", "UPSELL_EXTRA"})
    @DisplayName("quote-only names, EXTENSION, and unknown tokens fail loudly")
    void unmappedValuesFail(String crmDealType) {
        assertThatThrownBy(() -> CrmQuoteDealTypeMapping.toQuoteDealType(crmDealType))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("CRM_DEAL_TYPE_UNMAPPED");
    }

    @Test
    @DisplayName("an unmapped enum-shaped token is named in the error; a huge or marked-up value is not echoed")
    void unmappedErrorDoesNotEchoUnsafeInput() {
        assertThatThrownBy(() -> CrmQuoteDealTypeMapping.toQuoteDealType("extension"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("EXTENSION");

        String hostile = "<script>" + "X".repeat(80);
        assertThatThrownBy(() -> CrmQuoteDealTypeMapping.toQuoteDealType(hostile))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("(rejected)")
                .hasMessageNotContaining("<script>");
    }
}
