package com.opencrm.pricing.openl;

import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenLPricingTableImportServiceTest {

    private final RuleSetRepository ruleSetRepository = mock(RuleSetRepository.class);
    private final OpenLPricingTableImportService service = new OpenLPricingTableImportService(ruleSetRepository);

    @Test
    void validatesImportablePricingDiscountTable() {
        when(ruleSetRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(RuleSet.builder().id(42L).build()));

        OpenLPricingTableImportResult result = service.validate(OpenLPricingTableImportRequest.builder()
                .ruleSetId(42L)
                .tableName("named-seat-volume-discounts")
                .csvContent("""
                        rule_name,product_code,min_quantity,discount_percent,priority
                        Volume 100,SKU-SEAT,100,7.5,620
                        Volume 500,SKU-SEAT,500,12,621
                        """)
                .build());

        assertThat(result.isImportable()).isTrue();
        assertThat(result.getTotalRows()).isEqualTo(2);
        assertThat(result.getValidRows()).isEqualTo(2);
        assertThat(result.getIssues()).isEmpty();
    }

    @Test
    void reportsHeaderAndValueIssuesBeforeImport() {
        when(ruleSetRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(RuleSet.builder().id(42L).build()));

        OpenLPricingTableImportResult result = service.validate(OpenLPricingTableImportRequest.builder()
                .ruleSetId(42L)
                .tableName("bad-discounts")
                .csvContent("""
                        rule_name,product_code,min_quantity,discount_percent,priority
                        Volume 100,SKU-SEAT,0,120,99
                        """)
                .build());

        assertThat(result.isImportable()).isFalse();
        assertThat(result.getIssues()).extracting(OpenLPricingTableValidationIssue::getCode)
                .containsExactly("VALUE_OUT_OF_RANGE", "VALUE_OUT_OF_RANGE", "VALUE_OUT_OF_RANGE");
    }
}
