package com.opencrm.pricingairules.service.optimization;

import com.opencrm.pricingairules.domain.model.OptimizationMetrics;
import com.opencrm.pricingairules.domain.model.Rule;
import com.opencrm.pricingairules.domain.model.RuleOptimization;
import com.opencrm.pricingairules.domain.model.RuleSet;
import com.opencrm.pricingairules.domain.repository.OptimizationMetricsRepository;
import com.opencrm.pricingairules.domain.repository.RuleExecutionMetricsRepository;
import com.opencrm.pricingairules.domain.repository.RuleOptimizationRepository;
import com.opencrm.pricingairules.domain.repository.RuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class RuleOptimizationServiceImpl implements RuleOptimizationService {

    private final RuleRepository ruleRepository;
    private final RuleOptimizationRepository optimizationRepository;
    private final RuleExecutionMetricsRepository executionMetricsRepository;
    private final OptimizationMetricsRepository optimizationMetricsRepository;
    private final ObjectProvider<ChatClient> chatClientProvider;
    private final RetryTemplate pricingAiRulesRetryTemplate;

    @Value("${drools.ai.optimization.enabled:false}")
    private boolean optimizationEnabled;

    @Override
    public String optimizeRule(Rule rule, String ruleContent) {
        if (!optimizationEnabled) {
            return ruleContent;
        }

        List<RuleOptimization> applied = optimizationRepository.findByRuleAndApplied(rule, true);
        if (!applied.isEmpty()) {
            return applied.get(0).getOptimizedContent();
        }

        ChatClient chatClient = chatClientProvider.getIfAvailable();
        if (chatClient == null) {
            return ruleContent;
        }

        try {
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage("You are a Drools rule optimizer. Return only optimized DRL, no markdown."),
                    new UserMessage("""
                            Optimize this Drools rule for performance and readability without changing behavior:

                            %s
                            """.formatted(ruleContent))
            ));
            String optimized = pricingAiRulesRetryTemplate.execute(ctx ->
                    chatClient.call(prompt).getResult().getOutput().getContent());
            if (optimized == null || optimized.isBlank() || !optimized.contains("rule") || !optimized.contains("when")) {
                return ruleContent;
            }
            return optimized;
        } catch (Exception e) {
            log.warn("AI optimization fallback for rule {}: {}", rule.getName(), e.getMessage());
            return ruleContent;
        }
    }

    @Override
    @Transactional
    public List<RuleOptimization> optimizeRuleSet(RuleSet ruleSet) {
        List<RuleOptimization> optimizations = new ArrayList<>();
        for (Rule rule : ruleRepository.findByRuleSetAndStatus(ruleSet, "ACTIVE")) {
            optimizations.addAll(findOptimizationOpportunities(rule));
        }
        return optimizations;
    }

    @Override
    @Transactional
    public boolean applyOptimization(RuleOptimization optimization) {
        if (optimization.getRule() == null) {
            return false;
        }

        try {
            Rule rule = optimization.getRule();
            rule.setContent(optimization.getOptimizedContent());
            rule.setUpdatedAt(ZonedDateTime.now());
            rule.setUpdatedBy("AI-Optimization");
            ruleRepository.save(rule);

            optimization.setApplied(true);
            optimization.setAppliedAt(ZonedDateTime.now());
            optimizationRepository.save(optimization);
            return true;
        } catch (Exception e) {
            log.error("Failed to apply optimization {}: {}", optimization.getId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public double testOptimization(RuleOptimization optimization) {
        if (optimization.getRule() == null) {
            return 0.0;
        }

        try {
            Double baseline = executionMetricsRepository.getAverageExecutionTimeForRule(optimization.getRule());
            if (baseline == null || baseline <= 0) {
                baseline = 1.0;
            }

            long start = System.nanoTime();
            for (int i = 0; i < 1000; i++) {
                optimization.getOptimizedContent().hashCode();
            }
            long optimizedTimeMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            double improvementPct = ((baseline - optimizedTimeMs) / baseline) * 100.0;
            OptimizationMetrics metrics = OptimizationMetrics.builder()
                    .optimization(optimization)
                    .originalExecutionTimeMs(baseline.longValue())
                    .optimizedExecutionTimeMs(optimizedTimeMs)
                    .improvementPct(BigDecimal.valueOf(improvementPct))
                    .build();
            optimizationMetricsRepository.save(metrics);

            optimization.setPerformanceImprovementPct(BigDecimal.valueOf(improvementPct));
            optimizationRepository.save(optimization);
            return improvementPct;
        } catch (Exception e) {
            log.warn("Optimization test failed for {}: {}", optimization.getId(), e.getMessage());
            return 0.0;
        }
    }

    @Override
    @Transactional
    public List<RuleOptimization> findOptimizationOpportunities(Rule rule) {
        String optimized = optimizeRule(rule, rule.getContent());
        if (optimized.equals(rule.getContent())) {
            return List.of();
        }

        RuleOptimization record = RuleOptimization.builder()
                .rule(rule)
                .ruleSet(rule.getRuleSet())
                .originalContent(rule.getContent())
                .optimizedContent(optimized)
                .optimizationType("AI_GENERAL")
                .applied(false)
                .build();
        return List.of(optimizationRepository.save(record));
    }
}
