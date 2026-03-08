package com.opencrm.dealdesk.rules.service;

import com.opencrm.dealdesk.exception.DealNotFoundException;
import com.opencrm.dealdesk.model.Deal;
import com.opencrm.dealdesk.repository.DealRepository;
import com.opencrm.dealdesk.rules.core.DynamicSpelRule;
import com.opencrm.dealdesk.rules.dto.RuleExecutionResponseDto;
import com.opencrm.dealdesk.rules.exception.RuleDefinitionValidationException;
import com.opencrm.dealdesk.rules.model.RuleDefinitionEntity;
import com.opencrm.dealdesk.rules.repository.RuleDefinitionRepository;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.core.DefaultRulesEngine;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RuleExecutionService {

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final DealRepository dealRepository;
    private final DefaultRulesEngine rulesEngine;
    private final ExpressionParser expressionParser;
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    public RuleExecutionService(
            RuleDefinitionRepository ruleDefinitionRepository,
            DealRepository dealRepository
    ) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.dealRepository = dealRepository;
        this.rulesEngine = new DefaultRulesEngine();
        this.expressionParser = new SpelExpressionParser();
    }

    @Transactional(readOnly = true)
    public RuleExecutionResponseDto executeDealRules(Long dealId, String category) {
        String normalizedCategory = normalizeRequired(category, "category is required");
        Deal deal = dealRepository.findByIdAndDeletedFalse(dealId)
                .orElseThrow(() -> new DealNotFoundException(dealId));

        List<RuleDefinitionEntity> activeRules = ruleDefinitionRepository
                .findAllByCategoryIgnoreCaseAndEnabledAndDeletedFalseOrderByPriorityAscCreatedAtDesc(
                        normalizedCategory,
                        true
                );

        Facts facts = new Facts();
        Map<String, Object> outputs = new LinkedHashMap<>();
        prepareFacts(deal, facts, outputs);

        List<String> matchedRuleKeys = new ArrayList<>();
        List<String> matchedRuleNames = new ArrayList<>();
        List<String> firedRuleKeys = new ArrayList<>();
        List<String> firedRuleNames = new ArrayList<>();

        Rules executableRules = new Rules();
        for (RuleDefinitionEntity ruleEntity : activeRules) {
            Expression condition = parseExpression(ruleEntity.getConditionExpression(), "conditionExpression", ruleEntity);
            boolean matched = evaluateCondition(condition, facts);
            if (!matched) {
                continue;
            }

            String displayName = displayName(ruleEntity);
            matchedRuleKeys.add(ruleEntity.getRuleKey());
            matchedRuleNames.add(displayName);

            Expression action = parseExpression(ruleEntity.getActionExpression(), "actionExpression", ruleEntity);
            executableRules.register(new DynamicSpelRule(
                    ruleEntity.getRuleKey(),
                    displayName,
                    ruleEntity.getDescription(),
                    safePriority(ruleEntity.getPriority()),
                    condition,
                    action,
                    firedRuleKeys,
                    firedRuleNames
            ));
        }

        if (!matchedRuleKeys.isEmpty()) {
            rulesEngine.fire(executableRules, facts);
        }

        return RuleExecutionResponseDto.builder()
                .dealId(deal.getId())
                .category(normalizedCategory)
                .evaluatedRules(activeRules.size())
                .matchedRules(matchedRuleKeys.size())
                .firedRules(firedRuleKeys.size())
                .matchedRuleKeys(matchedRuleKeys)
                .matchedRuleNames(matchedRuleNames)
                .firedRuleKeys(firedRuleKeys)
                .firedRuleNames(firedRuleNames)
                .facts(summarizeFacts(facts))
                .outputs(sanitizeMap(outputs))
                .build();
    }

    private void prepareFacts(Deal deal, Facts facts, Map<String, Object> outputs) {
        facts.put("deal", deal);
        facts.put("dealId", deal.getId());
        facts.put("dealStatus", deal.getStatus() == null ? null : deal.getStatus().name());
        facts.put("dealValue", deal.getValue());
        facts.put("productIdsCount", deal.getProductIds() == null ? 0 : deal.getProductIds().size());
        facts.put("outputs", outputs);
        facts.put("executionTimestamp", Instant.now().toString());
    }

    private Expression parseExpression(String value, String fieldName, RuleDefinitionEntity ruleEntity) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return expressionCache.computeIfAbsent(fieldName + "|" + value, key -> {
            try {
                return expressionParser.parseExpression(value);
            } catch (RuntimeException ex) {
                throw new RuleDefinitionValidationException(
                        "Invalid " + fieldName + " for rule " + ruleEntity.getRuleKey(),
                        ex
                );
            }
        });
    }

    private boolean evaluateCondition(Expression condition, Facts facts) {
        if (condition == null) {
            return true;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            facts.asMap().forEach(context::setVariable);
            context.setRootObject(facts.get("deal"));
            return Boolean.TRUE.equals(condition.getValue(context, Boolean.class));
        } catch (RuntimeException ex) {
            throw new RuleDefinitionValidationException("Failed to evaluate rule condition", ex);
        }
    }

    private Map<String, Object> summarizeFacts(Facts facts) {
        Map<String, Object> summary = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : facts.asMap().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("deal".equals(key) && value instanceof Deal deal) {
                summary.put("deal", summarizeDeal(deal));
                continue;
            }
            summary.put(key, sanitizeValue(value));
        }
        return summary;
    }

    private Map<String, Object> summarizeDeal(Deal deal) {
        Map<String, Object> dealSummary = new LinkedHashMap<>();
        dealSummary.put("id", deal.getId());
        dealSummary.put("uuid", deal.getUuid());
        dealSummary.put("name", deal.getName());
        dealSummary.put("status", deal.getStatus() == null ? null : deal.getStatus().name());
        dealSummary.put("value", deal.getValue());
        dealSummary.put("accountId", deal.getAccountId());
        dealSummary.put("accountName", deal.getAccountName());
        dealSummary.put("salesforceOpportunityId", deal.getSalesforceOpportunityId());
        dealSummary.put("synced", deal.isSynced());
        dealSummary.put("productIds", deal.getProductIds() == null ? List.of() : new ArrayList<>(deal.getProductIds()));
        return dealSummary;
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> map) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(Object value) {
        if (value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            return value;
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Instant || value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            return value.toString();
        }
        if (value instanceof BigDecimal) {
            return value;
        }
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                nested.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue()));
            }
            return nested;
        }
        if (value instanceof List<?> listValue) {
            List<Object> list = new ArrayList<>();
            for (Object item : listValue) {
                list.add(sanitizeValue(item));
            }
            return list;
        }
        if (value instanceof Set<?> setValue) {
            List<Object> list = new ArrayList<>();
            for (Object item : new LinkedHashSet<>(setValue)) {
                list.add(sanitizeValue(item));
            }
            return list;
        }
        if (value instanceof Deal deal) {
            return summarizeDeal(deal);
        }
        return String.valueOf(value);
    }

    private String normalizeRequired(String value, String message) {
        String normalized = value == null ? null : value.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new RuleDefinitionValidationException(message);
        }
        return normalized;
    }

    private int safePriority(Integer priority) {
        return priority == null ? Integer.MAX_VALUE : priority;
    }

    private String displayName(RuleDefinitionEntity ruleEntity) {
        return StringUtils.hasText(ruleEntity.getName()) ? ruleEntity.getName() : ruleEntity.getRuleKey();
    }
}
