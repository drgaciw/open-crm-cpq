package com.opencrm.rules.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRuleEnginePortTest {

    @Test
    void executesBusinessTablesBeforeProgrammaticRulesAndPreservesOneDeterministicTrace() {
        RecordingRulesEngine openL = new RecordingRulesEngine(RulesEngineType.OPENL_TABLETS);
        RecordingRulesEngine drools = new RecordingRulesEngine(RulesEngineType.DROOLS);
        RuleEnginePort port = new HybridRuleEnginePort(List.of(drools, openL));

        RuleDefinition droolsRule = RuleDefinition.builder()
                .id("margin-guardrail")
                .name("Margin Guardrail")
                .engineType("DROOLS")
                .priority(20)
                .build();
        RuleDefinition openLRule = RuleDefinition.builder()
                .id("volume-discount")
                .name("Volume Discount")
                .engineType("OPENL_TABLETS")
                .priority(10)
                .build();

        RuleExecutionResult result = port.execute(RuleExecutionRequest.builder()
                .context(RuleExecutionContext.builder()
                        .ruleSetId("pricing")
                        .ruleSetVersion("v1")
                        .build())
                .rules(List.of(droolsRule, openLRule))
                .build());

        assertThat(openL.executedRuleIds).containsExactly("volume-discount");
        assertThat(drools.executedRuleIds).containsExactly("margin-guardrail");
        assertThat(result.getFiredRuleNames()).containsExactly("Volume Discount", "Margin Guardrail");
        assertThat(result.getTrace()).extracting(RuleExecutionTraceEntry::getSequence)
                .containsExactly(1, 2);
        assertThat(result.getTrace()).extracting(RuleExecutionTraceEntry::getEngineType)
                .containsExactly(RulesEngineType.OPENL_TABLETS, RulesEngineType.DROOLS);
        assertThat(result.getTrace()).extracting(RuleExecutionTraceEntry::getRuleId)
                .containsExactly("volume-discount", "margin-guardrail");
    }

    private static final class RecordingRulesEngine implements RulesEngine {
        private final RulesEngineType engineType;
        private List<String> executedRuleIds = List.of();

        private RecordingRulesEngine(RulesEngineType engineType) {
            this.engineType = engineType;
        }

        @Override
        public RuleExecutionResult execute(RuleExecutionContext context) {
            throw new UnsupportedOperationException("Use execute(context, rules)");
        }

        @Override
        public RuleExecutionResult execute(RuleExecutionContext context, List<RuleDefinition> rules) {
            executedRuleIds = rules.stream()
                    .map(RuleDefinition::getId)
                    .toList();

            return RuleExecutionResult.builder()
                    .success(true)
                    .rulesFired(rules.size())
                    .firedRuleNames(rules.stream().map(RuleDefinition::getName).toList())
                    .build();
        }

        @Override
        public void invalidateCache(String ruleSetId) {
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public RulesEngineType getEngineType() {
            return engineType;
        }
    }
}
