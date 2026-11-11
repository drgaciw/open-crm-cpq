package com.opencrm.configuration.constraint;

import com.opencrm.configuration.product.ProductConfigurationRuleClient;
import com.opencrm.configuration.product.ProductConfigurationRuleDto;
import com.opencrm.configuration.session.ConfigurationSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExplicitDefaultsConstraintTest {
    @Test
    void missingRequiredTargetCannotBeSatisfiedByAnUnpersistedImplicitDefault() {
        var client = mock(ProductConfigurationRuleClient.class);
        when(client.getRulesForProduct(10L)).thenReturn(List.of(
                ProductConfigurationRuleDto.builder().sourceProductId(10L).targetProductId(20L).ruleType("DEFAULT").build(),
                ProductConfigurationRuleDto.builder().sourceProductId(10L).targetProductId(20L).ruleType("REQUIRES").build()));
        when(client.getRulesForProduct(20L)).thenReturn(List.of());
        var solver = new ConstraintSolver(client);
        var session = ConfigurationSession.builder().productId("10").selections(Map.of()).build();

        assertThat(solver.evaluate(session).isValid()).isTrue(); // Existing evaluate contract is unchanged.
        assertThat(solver.evaluateExplicitSelections(session).isValid()).isFalse();
        session.setSelections(Map.of("option:1:11", "20"));
        assertThat(solver.evaluateExplicitSelections(session).isValid()).isTrue();
    }
}
