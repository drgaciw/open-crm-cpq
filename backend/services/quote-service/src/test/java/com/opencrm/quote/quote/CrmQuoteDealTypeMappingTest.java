package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Stream;

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

    static Stream<Arguments> tableRows() {
        return CrmQuoteDealTypeMapping.rows().entrySet().stream()
                .map(row -> Arguments.of(row.getKey(), row.getValue()));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("tableRows")
    @DisplayName("each CRM value maps to the single table entry")
    void mapsEveryCrmValue(String crmDealType, DealType expected) {
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType(crmDealType)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "NEW_BUSINESS, NEW",
            "EXISTING_BUSINESS, EXPANSION",
            "RENEWAL, RENEWAL",
            "UPSELL, EXPANSION",
            "CROSS_SELL, EXPANSION"
    })
    @DisplayName("each decided row is pinned; EXISTING_BUSINESS is the single flip in Source")
    void decidedRowsStayPinned(String crmDealType, DealType expected) {
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType(crmDealType)).isEqualTo(expected);
        assertThat(CrmQuoteDealTypeMapping.rows().get(crmDealType)).isEqualTo(expected);
    }

    @Test
    @DisplayName("no CRM type maps to AMENDMENT; amendment is typed continuation only")
    void noCrmTypeMapsToAmendment() {
        assertThat(CrmQuoteDealTypeMapping.rows().values()).doesNotContain(DealType.AMENDMENT);
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("EXISTING_BUSINESS")).isNotEqualTo(DealType.AMENDMENT);
    }

    @Test
    @DisplayName("closed pricing keys stay NEW, RENEWAL, and EXPANSION; the open row uses its table name")
    void mappedNamesAreThePricingRuleKeys() {
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("RENEWAL").name()).isEqualTo("RENEWAL");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("UPSELL").name()).isEqualTo("EXPANSION");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("CROSS_SELL").name()).isEqualTo("EXPANSION");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("NEW_BUSINESS").name()).isEqualTo("NEW");
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("EXISTING_BUSINESS").name())
                .isEqualTo(CrmQuoteDealTypeMapping.rows().get("EXISTING_BUSINESS").name());
        assertThat(Set.of("RENEWAL")).doesNotContain(
                CrmQuoteDealTypeMapping.toQuoteDealType("UPSELL").name(),
                CrmQuoteDealTypeMapping.toQuoteDealType("CROSS_SELL").name(),
                CrmQuoteDealTypeMapping.toQuoteDealType("NEW_BUSINESS").name());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "renewal, RENEWAL",
            "' Renewal ', RENEWAL",
            "upsell, EXPANSION",
            "cross_sell, EXPANSION",
            "new_business, NEW"
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
