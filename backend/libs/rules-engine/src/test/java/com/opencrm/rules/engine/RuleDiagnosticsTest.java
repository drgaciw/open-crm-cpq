package com.opencrm.rules.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleDiagnosticsTest {

    private final RuleDiagnostics diagnostics = new RuleDiagnostics();

    @Test
    void validatePublicationDetectsDependencyCycles() {
        RuleDefinition first = RuleDefinition.builder()
                .id("rule-a")
                .name("Rule A")
                .metadata(Map.of("dependsOn", "rule-b"))
                .build();
        RuleDefinition second = RuleDefinition.builder()
                .id("rule-b")
                .name("Rule B")
                .metadata(Map.of("dependsOn", "rule-a"))
                .build();

        RuleDiagnosticReport report = diagnostics.validatePublication(List.of(first, second));

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.getDiagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.getCode()).isEqualTo("RULE_DEPENDENCY_CYCLE");
                    assertThat(diagnostic.getSeverity()).isEqualTo(RuleDiagnosticSeverity.ERROR);
                    assertThat(diagnostic.getRuleIds()).containsExactly("rule-a", "rule-b", "rule-a");
                });
    }

    @Test
    void validatePublicationDetectsConflictingMutationsForSameActionSetAtSamePriority() {
        RuleDefinition first = RuleDefinition.builder()
                .id("volume-discount")
                .name("Volume Discount")
                .priority(620)
                .actionSet("line.discountRate")
                .build();
        RuleDefinition second = RuleDefinition.builder()
                .id("promo-discount")
                .name("Promotional Discount")
                .priority(620)
                .actionSet("line.discountRate")
                .build();

        RuleDiagnosticReport report = diagnostics.validatePublication(List.of(first, second));

        assertThat(report.hasErrors()).isTrue();
        assertThat(report.getDiagnostics())
                .anySatisfy(diagnostic -> {
                    assertThat(diagnostic.getCode()).isEqualTo("CONFLICTING_RULE_MUTATION");
                    assertThat(diagnostic.getSeverity()).isEqualTo(RuleDiagnosticSeverity.ERROR);
                    assertThat(diagnostic.getRuleIds()).containsExactlyInAnyOrder("volume-discount", "promo-discount");
                    assertThat(diagnostic.getMessage()).contains("line.discountRate");
                });
    }
}
