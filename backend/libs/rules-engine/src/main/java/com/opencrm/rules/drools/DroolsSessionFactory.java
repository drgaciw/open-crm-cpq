package com.opencrm.rules.drools;

import com.opencrm.rules.engine.RuleDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Factory for creating Drools KieSessions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DroolsSessionFactory {

    private final DroolsRuleCompiler compiler;

    /**
     * Create a new KieSession from rule definitions.
     */
    public KieSession createSession(List<RuleDefinition> rules) {
        log.debug("Creating KieSession from {} rules", rules.size());

        KieBase kieBase = compiler.compileRules(rules);
        return createSession(kieBase);
    }

    /**
     * Create a new KieSession from an existing KieBase.
     */
    public KieSession createSession(KieBase kieBase) {
        log.debug("Creating KieSession from KieBase");

        try {
            KieSession kieSession = kieBase.newKieSession();
            log.debug("Successfully created KieSession");
            return kieSession;
        } catch (Exception e) {
            log.error("Failed to create KieSession: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to create KieSession", e);
        }
    }
}
