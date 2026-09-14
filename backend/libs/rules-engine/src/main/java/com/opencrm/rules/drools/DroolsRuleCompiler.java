package com.opencrm.rules.drools;

import com.opencrm.rules.drools.helpers.DrlResource;
import com.opencrm.rules.drools.helpers.KieFileSystemCompiler;
import com.opencrm.rules.engine.RuleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieBase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles Drools rules from DRL strings into executable KieBases.
 *
 * <p>Compile/validate diagnostics follow source {@code DroolsServiceImpl} ({@code KieBuilder}
 * + ERROR-only failure). Public throwing APIs remain {@link IllegalArgumentException} for
 * existing session/cache callers.
 */
@Slf4j
@Component
public class DroolsRuleCompiler {

    /**
     * Compile multiple rule definitions into a KieBase.
     */
    public KieBase compileRules(List<RuleDefinition> rules) {
        log.debug("Compiling {} rules", rules == null ? 0 : rules.size());
        requireContent(rules);
        DroolsCompilationResult result = KieFileSystemCompiler.compile(toResources(rules));
        if (!result.isValid()) {
            log.error("Failed to build KieBase: {}", result.joinedErrors());
            throw new IllegalArgumentException("Failed to compile rule set: " + result.joinedErrors());
        }
        log.info("Successfully compiled {} rules into KieBase", rules.size());
        return result.getKieBase();
    }

    /**
     * Compile a single DRL string into a KieBase.
     */
    public KieBase compileFromDrlString(String drl) {
        log.debug("Compiling DRL string");
        DroolsCompilationResult result = validateDrl(drl);
        if (!result.isValid()) {
            log.error("Failed to compile DRL string: {}", result.joinedErrors());
            throw new IllegalArgumentException("Failed to compile DRL: " + result.joinedErrors());
        }
        log.debug("Successfully compiled DRL string");
        return result.getKieBase();
    }

    /**
     * Source {@code DroolsService.compileRule}: true when Kie compilation has no ERROR messages.
     * Empty or null content is a failed compile (does not throw).
     */
    public boolean compileRule(RuleDefinition rule) {
        if (rule == null || isBlank(rule.getContent())) {
            return false;
        }
        return KieFileSystemCompiler.compile(List.of(toResource(rule, 0))).isValid();
    }

    /**
     * Source {@code DroolsService.compileRuleSet}: compile every rule together.
     * An empty list is a successful no-op compile (create-set-with-no-rules golden).
     */
    public boolean compileRuleSet(List<RuleDefinition> rules) {
        if (rules == null) {
            return false;
        }
        for (RuleDefinition rule : rules) {
            if (rule == null || isBlank(rule.getContent())) {
                return false;
            }
        }
        return KieFileSystemCompiler.compile(toResources(rules)).isValid();
    }

    /**
     * Source {@code DroolsService.validateRules}: structured result, ERROR-only failure.
     */
    public DroolsCompilationResult validateDrl(String drl) {
        if (isBlank(drl)) {
            return DroolsCompilationResult.emptyContent("DRL content cannot be empty");
        }
        return KieFileSystemCompiler.compile(List.of(new DrlResource("src/main/resources/rule.drl", drl)));
    }

    /**
     * Validate a list of rule definitions without throwing.
     */
    public DroolsCompilationResult validateRules(List<RuleDefinition> rules) {
        if (rules == null) {
            return DroolsCompilationResult.emptyContent("Rule list cannot be null");
        }
        for (RuleDefinition rule : rules) {
            if (rule == null || isBlank(rule.getContent())) {
                String id = rule == null ? "null" : rule.getId();
                return DroolsCompilationResult.emptyContent("Rule " + id + " has no content");
            }
        }
        return KieFileSystemCompiler.compile(toResources(rules));
    }

    private static void requireContent(List<RuleDefinition> rules) {
        if (rules == null) {
            throw new IllegalArgumentException("Rule list cannot be null");
        }
        for (RuleDefinition rule : rules) {
            if (rule == null || isBlank(rule.getContent())) {
                String id = rule == null ? "null" : rule.getId();
                log.error("Rule {} has no content", id);
                throw new IllegalArgumentException("Rule " + id + " has no content");
            }
        }
    }

    private static List<DrlResource> toResources(List<RuleDefinition> rules) {
        List<DrlResource> resources = new ArrayList<>(rules.size());
        for (int i = 0; i < rules.size(); i++) {
            resources.add(toResource(rules.get(i), i));
        }
        return resources;
    }

    private static DrlResource toResource(RuleDefinition rule, int index) {
        String id = rule.getId() == null || rule.getId().isBlank() ? "anonymous" : rule.getId();
        String safeId = id.replaceAll("[^A-Za-z0-9._-]", "_");
        String path = "src/main/resources/rule_" + safeId + "_" + index + ".drl";
        return new DrlResource(path, rule.getContent());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
