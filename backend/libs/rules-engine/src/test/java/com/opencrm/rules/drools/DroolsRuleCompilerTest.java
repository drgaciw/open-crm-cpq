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

        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(RuleDefinition.builder()
                .id("rule-1")
                .name("Rule 1")
                .content(ruleContent1)
                .build());
        rules.add(RuleDefinition.builder()
                .id("rule-2")
                .name("Rule 2")
                .content(ruleContent2)
                .build());

        KieBase kieBase = compiler.compileRules(rules);

        assertThat(kieBase).isNotNull();
    }

    @Test
    void compileRules_ThrowsExceptionWhenRuleContentIsNull() {
        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(RuleDefinition.builder()
                .id("rule-null")
                .name("Null Content Rule")
                .content(null)
                .build());

        assertThatThrownBy(() -> compiler.compileRules(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule rule-null has no content");
    }

    @Test
    void compileRules_ThrowsExceptionWhenRuleContentIsEmpty() {
        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(RuleDefinition.builder()
                .id("rule-empty")
                .name("Empty Content Rule")
                .content("")
                .build());

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

        List<RuleDefinition> rules = new ArrayList<>();
        rules.add(RuleDefinition.builder()
                .id("rule-invalid")
                .name("Invalid Rule")
                .content(invalidRuleContent)
                .build());

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
}
