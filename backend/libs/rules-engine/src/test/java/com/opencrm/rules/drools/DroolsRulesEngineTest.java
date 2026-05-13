package com.opencrm.rules.drools;

import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleDiagnosticSeverity;
import com.opencrm.rules.engine.RuleDiagnostics;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DroolsRulesEngineTest {

    private final RuleDiagnostics diagnostics = new RuleDiagnostics();
    private final DroolsRulesEngine rulesEngine = new DroolsRulesEngine(
            new DroolsSessionFactory(new DroolsRuleCompiler(diagnostics)),
            diagnostics
    );

    @Test
    void executeReturnsRuntimeDiagnosticWhenRuleThrows() {
        RuleDefinition rule = RuleDefinition.builder()
                .id("throwing-rule")
                .name("Throwing Rule")
                .content("""
                        package com.opencrm.rules.test;
                        rule "Throwing Rule"
                        when
                            $value: String()
                        then
                            throw new RuntimeException("boom");
                        end
                        """)
                .build();
        RuleExecutionContext context = RuleExecutionContext.builder()
                .facts(List.of("input"))
                .build();

        RuleExecutionResult result = rulesEngine.execute(context, List.of(rule));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getDiagnostics().hasErrors()).isTrue();
        assertThat(result.getDiagnostics().getDiagnostics())
                .singleElement()
                .satisfies(diagnostic -> {
                    assertThat(diagnostic.getSeverity()).isEqualTo(RuleDiagnosticSeverity.ERROR);
                    assertThat(diagnostic.getCode()).isEqualTo("RULE_RUNTIME_FAILURE");
                    assertThat(diagnostic.getRuleIds()).containsExactly("throwing-rule");
                    assertThat(diagnostic.getMessage()).contains("boom");
                });
    }
}
