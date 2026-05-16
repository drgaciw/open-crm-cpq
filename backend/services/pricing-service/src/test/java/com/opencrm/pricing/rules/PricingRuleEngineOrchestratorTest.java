package com.opencrm.pricing.rules;

import com.opencrm.pricing.rule.Rule;
import com.opencrm.pricing.rule.RuleType;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleEnginePort;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionRequest;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RuleExecutionTraceEntry;
import com.opencrm.rules.engine.RulesEngineType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingRuleEngineOrchestratorTest {

    @Mock
    private RuleEnginePort ruleEnginePort;

    private PricingRuleEngineOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new PricingRuleEngineOrchestrator(ruleEnginePort);
    }

    @Test
    void routesOpenLAndDroolsRulesThroughHybridPortWithDeterministicTrace() {
        RuleSet ruleSet = RuleSet.builder()
                .id(1L)
                .engineType("HYBRID")
                .build();
        Rule openLRule = Rule.builder()
                .id(10L)
                .name("Volume table")
                .ruleType(RuleType.PRICING)
                .priority(100)
                .content("openl-table:discountRate=0.10")
                .engineType("OPENL_TABLETS")
                .build();
        Rule droolsRule = Rule.builder()
                .id(11L)
                .name("Margin guardrail")
                .ruleType(RuleType.PRICING)
                .priority(200)
                .content("package com.opencrm.pricing")
                .build();

        when(ruleEnginePort.execute(any())).thenReturn(RuleExecutionResult.builder()
                .success(true)
                .trace(List.of(
                        RuleExecutionTraceEntry.builder()
                                .sequence(1)
                                .engineType(RulesEngineType.OPENL_TABLETS)
                                .ruleId("10")
                                .build(),
                        RuleExecutionTraceEntry.builder()
                                .sequence(2)
                                .engineType(RulesEngineType.DROOLS)
                                .ruleId("11")
                                .build()))
                .build());

        RuleExecutionContext context = RuleExecutionContext.builder()
                .ruleSetId("1")
                .build();

        RuleExecutionResult result = orchestrator.execute(ruleSet, List.of(openLRule, droolsRule), context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTrace()).hasSize(2);

        ArgumentCaptor<RuleExecutionRequest> requestCaptor = ArgumentCaptor.forClass(RuleExecutionRequest.class);
        verify(ruleEnginePort).execute(requestCaptor.capture());
        List<RuleDefinition> definitions = requestCaptor.getValue().getRules();
        assertThat(definitions).extracting(RuleDefinition::getEngineType)
                .containsExactly("OPENL_TABLETS", "DROOLS");
    }

    @Test
    void inheritsEngineTypeFromRuleSetWhenRuleOmitsEngineType() {
        RuleSet ruleSet = RuleSet.builder().id(2L).engineType("OPENL_TABLETS").build();
        Rule rule = Rule.builder()
                .id(20L)
                .name("Tier discount")
                .ruleType(RuleType.PRICING)
                .priority(150)
                .content("openl-table:discountRate=0.05")
                .build();

        RuleDefinition definition = orchestrator.toDefinition(rule, ruleSet);

        assertThat(definition.getEngineType()).isEqualTo("OPENL_TABLETS");
    }
}
