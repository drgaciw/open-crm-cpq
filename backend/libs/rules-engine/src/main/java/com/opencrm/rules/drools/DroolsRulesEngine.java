package com.opencrm.rules.drools;

import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RulesEngine;
import com.opencrm.rules.engine.RulesEngineType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Drools-based rules engine implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DroolsRulesEngine implements RulesEngine {

    private final DroolsSessionFactory sessionFactory;

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context) {
        log.info("Executing Drools rules for ruleSetId: {}", context.getRuleSetId());
        throw new UnsupportedOperationException("Dynamic rule loading not yet implemented - use execute(context, rules)");
    }

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context, List<RuleDefinition> rules) {
        log.info("Executing {} Drools rules", rules.size());

        long startTime = System.currentTimeMillis();
        KieSession kieSession = null;

        try {
            // Create session from rule definitions
            kieSession = sessionFactory.createSession(rules);

            // Insert all facts
            for (Object fact : context.getFacts()) {
                log.debug("Inserting fact: {}", fact.getClass().getSimpleName());
                kieSession.insert(fact);
            }

            // Set globals
            for (Map.Entry<String, Object> entry : context.getGlobals().entrySet()) {
                log.debug("Setting global: {} = {}", entry.getKey(), entry.getValue());
                kieSession.setGlobal(entry.getKey(), entry.getValue());
            }

            // Fire all rules
            int rulesFired = kieSession.fireAllRules();
            log.info("Fired {} rules", rulesFired);

            // Extract modified facts
            List<Object> modifiedFacts = new ArrayList<>();
            kieSession.getObjects().forEach(modifiedFacts::add);

            long executionTime = System.currentTimeMillis() - startTime;

            return RuleExecutionResult.builder()
                    .success(true)
                    .modifiedFacts(modifiedFacts)
                    .rulesFired(rulesFired)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("Failed to execute Drools rules: {}", e.getMessage(), e);
            throw new RuntimeException("Rule execution failed: " + e.getMessage(), e);

        } finally {
            if (kieSession != null) {
                kieSession.dispose();
                log.debug("Disposed KieSession");
            }
        }
    }

    @Override
    public void invalidateCache(String ruleSetId) {
        log.info("Invalidating cache for ruleSetId: {}", ruleSetId);
        // Cache invalidation will be handled by RulesCacheManager
    }

    @Override
    public boolean isHealthy() {
        try {
            // Try to compile a trivial rule to verify Drools is operational
            String testDrl = "package test; rule \"health-check\" when then end";
            sessionFactory.createSession(List.of(
                    RuleDefinition.builder()
                            .id("health-check")
                            .name("Health Check")
                            .content(testDrl)
                            .build()
            )).dispose();
            return true;
        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public RulesEngineType getEngineType() {
        return RulesEngineType.DROOLS;
    }
}
