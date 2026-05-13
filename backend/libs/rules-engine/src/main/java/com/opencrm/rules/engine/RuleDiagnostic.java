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
public class RuleDiagnostic {

    private RuleDiagnosticSeverity severity;
    private String code;
    private String message;

    @Builder.Default
    private List<String> ruleIds = new ArrayList<>();
}
