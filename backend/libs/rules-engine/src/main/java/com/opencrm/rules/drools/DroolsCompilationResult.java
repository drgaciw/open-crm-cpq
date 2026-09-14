package com.opencrm.rules.drools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.kie.api.KieBase;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Structured compile/validate outcome (source {@code RuleValidationResult} adapted to JDK 21).
 *
 * <p>{@code valid} is true when there are no ERROR-level diagnostics. Warnings and infos do not fail compilation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DroolsCompilationResult {

    private boolean valid;
    private List<DroolsCompilationMessage> errors;
    private List<DroolsCompilationMessage> warnings;
    private List<DroolsCompilationMessage> infos;
    private KieBase kieBase;

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    public String joinedErrors() {
        if (!hasErrors()) {
            return "";
        }
        return errors.stream()
                .map(DroolsCompilationMessage::format)
                .collect(Collectors.joining(", "));
    }

    public static DroolsCompilationResult emptyContent(String message) {
        return DroolsCompilationResult.builder()
                .valid(false)
                .errors(List.of(DroolsCompilationMessage.builder()
                        .code("EMPTY")
                        .message(message)
                        .severity(DroolsCompilationMessage.Severity.ERROR)
                        .build()))
                .warnings(Collections.emptyList())
                .infos(Collections.emptyList())
                .build();
    }
}
