package com.opencrm.rules.engine;

public class RuleDiagnosticsException extends IllegalArgumentException {

    private final RuleDiagnosticReport report;

    public RuleDiagnosticsException(RuleDiagnosticReport report) {
        super("Rule diagnostics failed: " + report.getDiagnostics().size() + " diagnostic(s)");
        this.report = report;
    }

    public RuleDiagnosticReport getReport() {
        return report;
    }
}
