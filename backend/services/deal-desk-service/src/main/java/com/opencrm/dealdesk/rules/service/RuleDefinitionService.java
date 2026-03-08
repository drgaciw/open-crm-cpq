package com.opencrm.dealdesk.rules.service;

import com.opencrm.dealdesk.rules.config.RuleEngineProperties;
import com.opencrm.dealdesk.rules.dto.RuleDefinitionRequestDto;
import com.opencrm.dealdesk.rules.dto.RuleDefinitionResponseDto;
import com.opencrm.dealdesk.rules.exception.RuleDefinitionConflictException;
import com.opencrm.dealdesk.rules.exception.RuleDefinitionNotFoundException;
import com.opencrm.dealdesk.rules.exception.RuleDefinitionValidationException;
import com.opencrm.dealdesk.rules.model.RuleDefinitionEntity;
import com.opencrm.dealdesk.rules.repository.RuleDefinitionRepository;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class RuleDefinitionService {

    private static final Pattern TYPE_REFERENCE_PATTERN = Pattern.compile("(^|[^\\w])T\\s*\\(");

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final RuleEngineProperties ruleEngineProperties;
    private final ExpressionParser expressionParser;

    public RuleDefinitionService(
            RuleDefinitionRepository ruleDefinitionRepository,
            RuleEngineProperties ruleEngineProperties
    ) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.ruleEngineProperties = ruleEngineProperties;
        this.expressionParser = new SpelExpressionParser();
    }

    @Transactional(readOnly = true)
    public List<RuleDefinitionResponseDto> listRules(String category, Boolean active) {
        String normalizedCategory = trimToNull(category);
        List<RuleDefinitionEntity> entities;

        if (normalizedCategory != null && active != null) {
            entities = ruleDefinitionRepository
                    .findAllByCategoryIgnoreCaseAndEnabledAndDeletedFalseOrderByPriorityAscCreatedAtDesc(
                            normalizedCategory, active
                    );
        } else if (normalizedCategory != null) {
            entities = ruleDefinitionRepository
                    .findAllByCategoryIgnoreCaseAndDeletedFalseOrderByPriorityAscCreatedAtDesc(normalizedCategory);
        } else if (active != null) {
            entities = ruleDefinitionRepository
                    .findAllByEnabledAndDeletedFalseOrderByPriorityAscCreatedAtDesc(active);
        } else {
            entities = ruleDefinitionRepository.findAllByDeletedFalseOrderByPriorityAscCreatedAtDesc();
        }

        return entities.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<RuleDefinitionResponseDto> listRulesByCategory(String category) {
        return listRules(category, null);
    }

    @Transactional(readOnly = true)
    public List<RuleDefinitionResponseDto> listActiveRulesByCategory(String category) {
        return listRules(category, true);
    }

    @Transactional(readOnly = true)
    public RuleDefinitionResponseDto getRuleById(Long id) {
        return toDto(getEntityById(id));
    }

    @Transactional
    public RuleDefinitionResponseDto createRule(RuleDefinitionRequestDto request) {
        RulePayload payload = validateAndNormalize(request);
        if (ruleDefinitionRepository.existsByRuleKeyAndDeletedFalse(payload.ruleKey())) {
            throw new RuleDefinitionConflictException("Rule key already exists: " + payload.ruleKey());
        }

        RuleDefinitionEntity entity = new RuleDefinitionEntity();
        applyPayload(entity, payload);
        return toDto(ruleDefinitionRepository.save(entity));
    }

    @Transactional
    public RuleDefinitionResponseDto updateRule(Long id, RuleDefinitionRequestDto request) {
        RuleDefinitionEntity entity = getEntityById(id);
        RulePayload payload = validateAndNormalize(request);

        if (ruleDefinitionRepository.existsByRuleKeyAndDeletedFalseAndIdNot(payload.ruleKey(), id)) {
            throw new RuleDefinitionConflictException("Rule key already exists: " + payload.ruleKey());
        }

        applyPayload(entity, payload);
        return toDto(ruleDefinitionRepository.save(entity));
    }

    @Transactional
    public void deleteRule(Long id) {
        RuleDefinitionEntity entity = getEntityById(id);
        entity.softDelete();
        ruleDefinitionRepository.save(entity);
    }

    private RuleDefinitionEntity getEntityById(Long id) {
        return ruleDefinitionRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new RuleDefinitionNotFoundException(id));
    }

    private RulePayload validateAndNormalize(RuleDefinitionRequestDto request) {
        String ruleKey = requireNonBlank(request.getRuleKey(), "ruleKey is required");

        String category = trimToNull(request.getCategory());
        if (category == null) {
            category = ruleEngineProperties.getDefaults().getCategory();
        }

        Integer priority = request.getPriority() != null
                ? request.getPriority()
                : ruleEngineProperties.getDefaults().getPriority();

        String conditionExpression = trimToNull(request.getConditionExpression());
        String actionExpression = trimToNull(request.getActionExpression());

        validateSpelExpression(
                "conditionExpression",
                conditionExpression,
                ruleEngineProperties.getValidation().isRequireConditionExpression()
        );
        validateSpelExpression(
                "actionExpression",
                actionExpression,
                ruleEngineProperties.getValidation().isRequireActionExpression()
        );

        boolean enabled = request.getEnabled() == null || request.getEnabled();
        Map<String, Object> parameters = request.getParameters() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(request.getParameters());

        return new RulePayload(
                ruleKey,
                trimToNull(request.getName()),
                trimToNull(request.getDescription()),
                category,
                priority,
                conditionExpression,
                actionExpression,
                enabled,
                parameters
        );
    }

    private void applyPayload(RuleDefinitionEntity entity, RulePayload payload) {
        entity.setRuleKey(payload.ruleKey());
        entity.setName(payload.name());
        entity.setDescription(payload.description());
        entity.setCategory(payload.category());
        entity.setPriority(payload.priority());
        entity.setConditionExpression(payload.conditionExpression());
        entity.setActionExpression(payload.actionExpression());
        entity.setEnabled(payload.enabled());
        entity.setParametersJson(payload.parameters());
    }

    private void validateSpelExpression(String fieldName, String expression, boolean required) {
        if (expression == null || expression.isBlank()) {
            if (required) {
                throw new RuleDefinitionValidationException(fieldName + " is required");
            }
            return;
        }

        if (!ruleEngineProperties.getValidation().isSpelSyntaxValidationEnabled()) {
            return;
        }

        if (expression.length() > ruleEngineProperties.getValidation().getMaxExpressionLength()) {
            throw new RuleDefinitionValidationException(
                    fieldName + " exceeds max length of "
                            + ruleEngineProperties.getValidation().getMaxExpressionLength()
            );
        }

        if (!ruleEngineProperties.getValidation().isAllowTypeReferences()
                && TYPE_REFERENCE_PATTERN.matcher(expression).find()) {
            throw new RuleDefinitionValidationException(fieldName + " contains disallowed type references");
        }

        if (!ruleEngineProperties.getValidation().isAllowBeanReferences() && expression.contains("@")) {
            throw new RuleDefinitionValidationException(fieldName + " contains disallowed bean references");
        }

        try {
            expressionParser.parseExpression(expression);
        } catch (RuntimeException ex) {
            throw new RuleDefinitionValidationException("Invalid SpEL syntax in " + fieldName, ex);
        }
    }

    private String requireNonBlank(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new RuleDefinitionValidationException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RuleDefinitionResponseDto toDto(RuleDefinitionEntity entity) {
        return RuleDefinitionResponseDto.builder()
                .id(entity.getId())
                .uuid(entity.getUuid())
                .ruleKey(entity.getRuleKey())
                .name(entity.getName())
                .description(entity.getDescription())
                .category(entity.getCategory())
                .priority(entity.getPriority())
                .conditionExpression(entity.getConditionExpression())
                .actionExpression(entity.getActionExpression())
                .enabled(entity.isEnabled())
                .parameters(entity.getParametersJson() == null ? Map.of() : new LinkedHashMap<>(entity.getParametersJson()))
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private record RulePayload(
            String ruleKey,
            String name,
            String description,
            String category,
            Integer priority,
            String conditionExpression,
            String actionExpression,
            boolean enabled,
            Map<String, Object> parameters
    ) {
    }
}
