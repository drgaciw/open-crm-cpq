package com.opencrm.product.configrule;

import com.opencrm.product.catalog.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurationRuleServiceTest {

    @Mock
    private ConfigurationRuleRepository configRuleRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ConfigurationRuleMapper configRuleMapper;

    @InjectMocks
    private ConfigurationRuleService configurationRuleService;

    @Test
    void validateConfiguration_ReturnsErrorsForRequiresAndExcludes() {
        ConfigurationRule requiresRule = new ConfigurationRule();
        requiresRule.setSourceProductId(1L);
        requiresRule.setTargetProductId(2L);
        requiresRule.setRuleType(ConfigRuleType.REQUIRES);

        ConfigurationRule excludesRule = new ConfigurationRule();
        excludesRule.setSourceProductId(1L);
        excludesRule.setTargetProductId(3L);
        excludesRule.setRuleType(ConfigRuleType.EXCLUDES);

        when(configRuleRepository.findBySourceProductIdInAndRuleTypeInAndDeletedFalse(
                eq(List.of(1L, 3L)), any()))
                .thenReturn(List.of(requiresRule, excludesRule));

        List<String> errors = configurationRuleService.validateConfiguration(List.of(1L, 3L));

        assertThat(errors).hasSize(2);
        assertThat(errors).contains(
                "Product 1 requires product 2",
                "Product 1 is incompatible with product 3"
        );
    }
}
