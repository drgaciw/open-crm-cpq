package com.opencrm.rules.openl;

import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import com.opencrm.rules.engine.RulesEngine;
import com.opencrm.rules.engine.RulesEngineType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * OpenL Tablets-based rules engine implementation.
 *
 * Note: This is a placeholder implementation. OpenL Tablets integration
 * is complex and will be expanded in future iterations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenLRulesEngine implements RulesEngine {

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context) {
        log.warn("OpenL Tablets execution not yet implemented");
        throw new UnsupportedOperationException("OpenL Tablets execution not yet implemented - use Drools engine");
    }

    @Override
    public RuleExecutionResult execute(RuleExecutionContext context, List<RuleDefinition> rules) {
        log.warn("OpenL Tablets execution not yet implemented");
        throw new UnsupportedOperationException("OpenL Tablets execution not yet implemented - use Drools engine");
    }

    @Override
    public void invalidateCache(String ruleSetId) {
        log.warn("OpenL Tablets cache invalidation not yet implemented");
    }

    @Override
    public boolean isHealthy() {
        log.warn("OpenL Tablets health check not yet implemented");
        return false;
    }

    @Override
    public RulesEngineType getEngineType() {
        return RulesEngineType.OPENL_TABLETS;
    }
}
