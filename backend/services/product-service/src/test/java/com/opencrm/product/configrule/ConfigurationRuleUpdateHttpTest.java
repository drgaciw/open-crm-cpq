package com.opencrm.product.configrule;

import com.opencrm.product.catalog.Product;
import com.opencrm.product.catalog.ProductRepository;
import com.opencrm.product.config.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigurationRuleUpdateHttpTest {
    private final ConfigurationRuleRepository rules = mock(ConfigurationRuleRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ConfigurationRuleService service = new ConfigurationRuleService(rules, products,
            Mappers.getMapper(ConfigurationRuleMapper.class));
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ConfigurationRuleController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void createsDraftWithCustomErrorMessage() throws Exception {
        when(products.findByIdAndDeletedFalse(12L)).thenReturn(Optional.of(Product.builder().id(12L).build()));
        when(rules.save(any())).thenAnswer(call -> call.getArgument(0));

        mvc.perform(post("/api/v1/configuration/rules").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"New rule","ruleType":"REQUIRES","sourceProductId":12,"priority":3,
                 "status":"DRAFT","errorMessage":"Choose the compatible add-on"}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.errorMessage").value("Choose the compatible add-on"));
    }

    @Test
    void updatesRuleWithoutReplacingSourceAndStopsApplyingRetiredRules() throws Exception {
        ConfigurationRule rule = ConfigurationRule.builder().id(3L).name("Legacy").sourceProductId(12L)
                .targetProductId(13L).ruleType(ConfigRuleType.REQUIRES).status("ACTIVE").build();
        when(rules.findByIdAndDeletedFalse(3L)).thenReturn(Optional.of(rule));
        when(products.findByIdAndDeletedFalse(13L)).thenReturn(Optional.of(Product.builder().id(13L).build()));
        when(rules.save(rule)).thenReturn(rule);
        when(rules.findBySourceProductIdInAndDeletedFalse(List.of(12L))).thenReturn(List.of(rule));

        mvc.perform(put("/api/v1/configuration/rules/3").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"Retired rule","ruleType":"REQUIRES","targetProductId":13,"priority":10,
                 "status":"RETIRED","errorMessage":"Old rule"}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceProductId").value(12))
                .andExpect(jsonPath("$.data.status").value("RETIRED"));

        assertThat(service.validateConfiguration(List.of(12L))).isEmpty();
    }
}
