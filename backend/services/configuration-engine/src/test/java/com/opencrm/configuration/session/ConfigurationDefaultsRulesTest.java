package com.opencrm.configuration.session;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.configuration.constraint.ConstraintSolver;
import com.opencrm.configuration.mapper.ConfigurationSessionMapper;
import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import com.opencrm.configuration.product.ProductOptionDefaultsClient;
import com.opencrm.configuration.product.ProductOptionGroupSnapshot;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigurationDefaultsRulesTest {
    @Test
    void realRuleCoreRejectsConflictingDefaultBatchBeforeAnySessionMutation() {
        var repository = mock(ConfigurationSessionRepository.class);
        var catalog = mock(ProductOptionDefaultsClient.class);
        var rules = mock(ProductConfigurationRuleClient.class);
        var solver = new ConstraintSolver(rules);
        var service = new ConfigurationDefaultsService(repository, catalog, solver,
                Mappers.getMapper(ConfigurationSessionMapper.class));
        var session = ConfigurationSession.builder().productId("10").userId("seller")
                .status(ConfigurationStatus.IN_PROGRESS).selections(new HashMap<>()).build();
        when(repository.findById(1L)).thenReturn(Optional.of(session));
        when(catalog.getGroups(10L)).thenReturn(List.of(
                group(1, 11, 20), group(2, 21, 30)));
        when(rules.getRulesForProduct(10L)).thenReturn(List.of(
                rule(10, 20, "DEFAULT"), rule(10, 20, "REQUIRES")));
        when(rules.getRulesForProduct(20L)).thenReturn(List.of(rule(20, 30, "EXCLUDES")));
        when(rules.getRulesForProduct(30L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.applyDefaults(1L, "seller"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("DEFAULTS_CONSTRAINT_CONFLICT"));
        assertThat(session.getSelections()).isEmpty();
        assertThat(session.getDefaultedSelectionKeys()).isEmpty();
        verify(repository, never()).save(any());
    }

    private static ProductOptionGroupSnapshot group(long groupId, long optionId, long productId) {
        return new ProductOptionGroupSnapshot(groupId, 10L, "REQUIRED_SINGLE", 1, 1,
                List.of(new ProductOptionGroupSnapshot.Option(optionId, productId, true)));
    }

    private static ProductConfigurationRuleDto rule(long source, long target, String type) {
        return ProductConfigurationRuleDto.builder().sourceProductId(source).targetProductId(target)
                .ruleType(type).build();
    }
}
