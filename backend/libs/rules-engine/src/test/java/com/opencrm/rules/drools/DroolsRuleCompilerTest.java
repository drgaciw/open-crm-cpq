package com.opencrm.rules.drools;

import com.opencrm.rules.engine.RuleDefinition;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DroolsRuleCompilerTest {

    private final DroolsRuleCompiler compiler = new DroolsRuleCompiler();

    @Test
    void compileRules_SuccessfullyCompilesValidRules() {
        String ruleContent1 = """
                package com.opencrm.rules.test;
                rule "Test Rule 1"
                when
                then
                    System.out.println("Rule 1 Fired");
                end
                """;

        String ruleContent2 = """
                package com.opencrm.rules.test;
                rule "Test Rule 2"
                when
                then
                    System.out.println("Rule 2 Fired");
                end
                """;

        List<RuleDefinition> rules = List.of(
                createRule("rule-1", "Rule 1", ruleContent1),
                createRule("rule-2", "Rule 2", ruleContent2)
        );

        KieBase kieBase = compiler.compileRules(rules);

        assertThat(kieBase).isNotNull();
    }

    @Test
    void compileRules_ThrowsExceptionWhenRuleContentIsNull() {
        List<RuleDefinition> rules = List.of(
                createRule("rule-null", "Null Content Rule", null)
        );

        assertThatThrownBy(() -> compiler.compileRules(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule rule-null has no content");
    }

    @Test
    void compileRules_ThrowsExceptionWhenRuleContentIsEmpty() {
        List<RuleDefinition> rules = List.of(
                createRule("rule-empty", "Empty Content Rule", "")
        );

        assertThatThrownBy(() -> compiler.compileRules(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule rule-empty has no content");
    }

    @Test
    void compileRules_ThrowsExceptionWhenRuleContentIsInvalid() {
        String invalidRuleContent = """
                package com.opencrm.rules.test;
                rule "Invalid Rule"
                when
                    invalid syntax here
                then
                end
                """;

        List<RuleDefinition> rules = List.of(
                createRule("rule-invalid", "Invalid Rule", invalidRuleContent)
        );

        assertThatThrownBy(() -> compiler.compileRules(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to compile rule set");
    }

    @Test
    void compileFromDrlString_SuccessfullyCompilesValidString() {
        String drl = """
                package com.opencrm.rules.test;
                rule "String Rule"
                when
                then
                end
                """;

        KieBase kieBase = compiler.compileFromDrlString(drl);

        assertThat(kieBase).isNotNull();
    }

    @Test
    void compileFromDrlString_ThrowsExceptionWhenStringIsInvalid() {
        String invalidDrl = """
                package com.opencrm.rules.test;
                rule "Invalid String Rule"
                when
                    broken syntax
                then
                end
                """;

        assertThatThrownBy(() -> compiler.compileFromDrlString(invalidDrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Failed to compile DRL");
    }

    private RuleDefinition createRule(String id, String name, String content) {
        return RuleDefinition.builder()
                .id(id)
                .name(name)
                .content(content)
                .build();
    }
}
