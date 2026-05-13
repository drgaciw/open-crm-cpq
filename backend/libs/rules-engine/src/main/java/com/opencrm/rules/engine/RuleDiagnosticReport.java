package com.opencrm.rules.engine;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDiagnosticReport {

    @Builder.Default
    private List<RuleDiagnostic> diagnostics = new ArrayList<>();

    public boolean hasErrors() {
        return diagnostics.stream()
                .anyMatch(diagnostic -> diagnostic.getSeverity() == RuleDiagnosticSeverity.ERROR);
    }
}
