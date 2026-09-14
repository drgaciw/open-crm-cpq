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
        assertThat(kieBase.newKieSession()).isNotNull();
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
    void compileRules_ThrowsExceptionWhenRuleContentIsBlank() {
        List<RuleDefinition> rules = List.of(
                createRule("rule-blank", "Blank Content Rule", "   \n\t")
        );

        assertThatThrownBy(() -> compiler.compileRules(rules))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule rule-blank has no content");
    }

    @Test
    void compileRules_ThrowsExceptionWhenRuleListIsNull() {
        assertThatThrownBy(() -> compiler.compileRules(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rule list cannot be null");
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
    void compileRules_ThrowsExceptionWhenRuleIsMissingEnd() {
        String incomplete = """
                package com.opencrm.rules.test;
                rule "Missing End"
                when
                then
                """;

        assertThatThrownBy(() -> compiler.compileRules(List.of(createRule("rule-incomplete", "Missing End", incomplete))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to compile rule set");
    }

    @Test
    void compileRules_ThrowsExceptionWhenFactTypeIsUnknown() {
        String unknownType = """
                package com.opencrm.rules.test;
                rule "Unknown Fact"
                when
                    UnknownFact(id == 1)
                then
                end
                """;

        assertThatThrownBy(() -> compiler.compileRules(List.of(createRule("rule-unknown", "Unknown Fact", unknownType))))
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
                .hasMessageContaining("Failed to compile DRL");
    }

    @Test
    void compileFromDrlString_ThrowsExceptionWhenStringIsBlank() {
        assertThatThrownBy(() -> compiler.compileFromDrlString("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to compile DRL");
    }

    @Test
    void validateDrl_AcceptsMinimalValidRule() {
        DroolsCompilationResult result = compiler.validateDrl("""
                package com.example
                rule "ok"
                when
                then
                end
                """);

        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
        assertThat(result.getKieBase()).isNotNull();
    }

    @Test
    void validateDrl_ReturnsCompileErrorForInvalidSyntax() {
        DroolsCompilationResult result = compiler.validateDrl("""
                package com.opencrm.rules.test;
                rule "Invalid Rule"
                when
                    invalid syntax here
                then
                end
                """);

        assertThat(result.isValid()).isFalse();
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).allMatch(m -> m.getSeverity() == DroolsCompilationMessage.Severity.ERROR);
        assertThat(result.joinedErrors()).isNotBlank();
        assertThat(result.getKieBase()).isNull();
    }

    @Test
    void validateDrl_ReturnsEmptyErrorForBlankContent() {
        DroolsCompilationResult result = compiler.validateDrl(" \n ");

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).extracting(DroolsCompilationMessage::getCode).containsExactly("EMPTY");
    }

    @Test
    void validateDrl_WarningsDoNotFailCompilation() {
        DroolsCompilationResult result = compiler.validateDrl("""
                package com.opencrm.rules.test;
                import java.util.ArrayList;
                rule "Possibly Unused Import"
                when
                then
                end
                """);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getKieBase()).isNotNull();
    }

    @Test
    void compileRule_ReturnsFalseForInvalidAndEmpty() {
        assertThat(compiler.compileRule(null)).isFalse();
        assertThat(compiler.compileRule(createRule("e", "empty", ""))).isFalse();
        assertThat(compiler.compileRule(createRule("i", "invalid", "not a drl"))).isFalse();
        assertThat(compiler.compileRule(createRule("ok", "ok", """
                package com.example
                rule "ok"
                when
                then
                end
                """))).isTrue();
    }

    @Test
    void compileRuleSet_EmptyListSucceeds() {
        assertThat(compiler.compileRuleSet(List.of())).isTrue();
        assertThat(compiler.compileRuleSet(new ArrayList<>())).isTrue();
        assertThat(compiler.compileRuleSet(null)).isFalse();
    }

    @Test
    void compileRuleSet_FailsWhenAnyRuleIsInvalid() {
        RuleDefinition valid = createRule("ok", "ok", """
                package com.example
                rule "ok"
                when
                then
                end
                """);
        RuleDefinition invalid = createRule("bad", "bad", "not a drl");

        assertThat(compiler.compileRuleSet(List.of(valid, invalid))).isFalse();
        assertThat(compiler.compileRuleSet(List.of(valid))).isTrue();
    }

    @Test
    void validateRules_SurfacesEmptyContentWithoutThrowing() {
        DroolsCompilationResult result = compiler.validateRules(List.of(
                createRule("rule-empty", "Empty", "  ")
        ));

        assertThat(result.isValid()).isFalse();
        assertThat(result.joinedErrors()).contains("rule-empty");
    }

    private RuleDefinition createRule(String id, String name, String content) {
        return RuleDefinition.builder()
                .id(id)
                .name(name)
                .content(content)
                .build();
    }
}
