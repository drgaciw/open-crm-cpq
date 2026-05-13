package com.opencrm.rules.drools;

import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleDiagnosticReport;
import com.opencrm.rules.engine.RuleDiagnostics;
import com.opencrm.rules.engine.RuleDiagnosticsException;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compiles Drools rules from DRL strings into executable KieBases.
 */
@Slf4j
@Component
public class DroolsRuleCompiler {

    private final RuleDiagnostics diagnostics;

    public DroolsRuleCompiler() {
        this(new RuleDiagnostics());
    }

    public DroolsRuleCompiler(RuleDiagnostics diagnostics) {
        this.diagnostics = diagnostics;
    }

    /**
     * Compile multiple rule definitions into a KieBase.
     */
    public KieBase compileRules(List<RuleDefinition> rules) {
        log.debug("Compiling {} rules", rules.size());

        RuleDiagnosticReport report = diagnostics.validatePublication(rules);
        if (report.hasErrors()) {
            throw new RuleDiagnosticsException(report);
        }

        KieHelper kieHelper = new KieHelper();
        int compiledCount = 0;

        for (RuleDefinition rule : rules) {
            if (rule.getContent() == null || rule.getContent().isEmpty()) {
                log.error("Rule {} has no content", rule.getId());
                throw new IllegalArgumentException("Rule " + rule.getId() + " has no content");
            }

            try {
                kieHelper.addContent(rule.getContent(), ResourceType.DRL);
                log.debug("Added rule: {}", rule.getName());
                compiledCount++;
            } catch (Exception e) {
                log.error("Failed to add rule {}: {}", rule.getName(), e.getMessage(), e);
                throw new IllegalArgumentException("Failed to compile rule: " + rule.getName(), e);
            }
        }

        try {
            KieBase kieBase = kieHelper.build();
            log.info("Successfully compiled {} rules into KieBase", compiledCount);
            return kieBase;
        } catch (Exception e) {
            log.error("Failed to build KieBase: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to compile rule set", e);
        }
    }

    /**
     * Compile a single DRL string into a KieBase.
     */
    public KieBase compileFromDrlString(String drl) {
        log.debug("Compiling DRL string");

        KieHelper kieHelper = new KieHelper();

        try {
            kieHelper.addContent(drl, ResourceType.DRL);
            KieBase kieBase = kieHelper.build();
            log.debug("Successfully compiled DRL string");
            return kieBase;
        } catch (Exception e) {
            log.error("Failed to compile DRL string: {}", e.getMessage(), e);
            throw new IllegalArgumentException("Failed to compile DRL", e);
        }
    }
}
