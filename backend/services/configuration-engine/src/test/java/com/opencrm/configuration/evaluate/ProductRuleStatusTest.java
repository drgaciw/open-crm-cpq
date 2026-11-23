package com.opencrm.configuration.evaluate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRuleStatusTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void ignoresRetiredRequirementsAndDraftDefaultsButKeepsLegacyRules() throws Exception {
        ProductConfigurationRuleDto retired = json.readValue("""
                {"id":1,"ruleType":"REQUIRES","sourceProductId":10,"targetProductId":20,"status":"RETIRED"}
                """, ProductConfigurationRuleDto.class);
        ProductConfigurationRuleDto draft = json.readValue("""
                {"id":2,"ruleType":"DEFAULT","sourceProductId":10,"targetProductId":30,"status":"DRAFT"}
                """, ProductConfigurationRuleDto.class);
        ProductConfigurationRuleDto legacy = json.readValue("""
                {"id":3,"ruleType":"REQUIRES","sourceProductId":10,"targetProductId":40}
                """, ProductConfigurationRuleDto.class);

        var result = ProductRuleEvaluationCore.evaluate(List.of(10L),
                id -> id == 10L ? List.of(retired, draft, legacy) : List.of());

        assertThat(result.getViolations()).singleElement()
                .satisfies(violation -> assertThat(violation.getDescription()).contains("40"));
    }
}
