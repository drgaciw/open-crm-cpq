package com.opencrm.pricing.rule;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.execution.PricingAuditLogRepository;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.ruleset.RuleSetStatus;
import com.opencrm.rules.drools.DroolsCompilationResult;
import com.opencrm.rules.drools.DroolsRuleCompiler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for managing rules.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RuleService {

    private final RuleRepository ruleRepository;
    private final RuleSetRepository ruleSetRepository;
    private final PricingAuditLogRepository pricingAuditLogRepository;
    private final RuleMapper ruleMapper;
    private final DroolsRuleCompiler droolsRuleCompiler;

    /**
     * Create a new rule.
     */
    public RuleDto create(RuleCreateRequest request) {
        log.debug("Creating rule with name: {} for rule set ID: {}", request.getName(), request.getRuleSetId());

        // Verify rule set exists
        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(request.getRuleSetId())
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", request.getRuleSetId()));

        // Cannot add rules to active rule sets
        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot add rules to an ACTIVE rule set. Create a new version instead.",
                    "INVALID_STATE_TRANSITION"
            );
        }

        assertCompiles(request.getContent(), request.getName());

        Rule rule = ruleMapper.toEntity(request);
        Rule saved = ruleRepository.save(rule);
        log.info("Created rule with ID: {}", saved.getId());

        return ruleMapper.toDto(saved);
    }

    /**
     * Get rule by ID.
     */
    @Transactional(readOnly = true)
    public RuleDto getById(Long id) {
        log.debug("Fetching rule by ID: {}", id);

        Rule rule = ruleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", id));

        return ruleMapper.toDto(rule);
    }

    /**
     * Update a rule.
     */
    public RuleDto update(Long id, RuleUpdateRequest request) {
        log.debug("Updating rule ID: {}", id);

        Rule rule = ruleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", id));

        // Verify rule set exists and is editable
        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(rule.getRuleSetId())
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", rule.getRuleSetId()));

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot update rules in an ACTIVE rule set. Create a new version instead.",
                    "INVALID_STATE_TRANSITION"
            );
        }

        ruleMapper.updateEntity(request, rule);
        assertCompiles(rule.getContent(), rule.getName());

        Rule updated = ruleRepository.save(rule);
        log.info("Updated rule ID: {}", updated.getId());

        return ruleMapper.toDto(updated);
    }

    /**
     * Soft delete a rule.
     */
    public void delete(Long id) {
        log.debug("Deleting rule ID: {}", id);

        Rule rule = ruleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", id));

        // Verify rule set exists and is editable
        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(rule.getRuleSetId())
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", rule.getRuleSetId()));

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot delete rules from an ACTIVE rule set. Create a new version instead.",
                    "INVALID_STATE_TRANSITION"
            );
        }

        rule.softDelete();
        ruleRepository.save(rule);

        log.info("Deleted rule ID: {}", id);
    }

    /**
     * List rules by rule set ID.
     */
    @Transactional(readOnly = true)
    public Page<RuleDto> listByRuleSet(Long ruleSetId, Pageable pageable) {
        log.debug("Listing rules for rule set ID: {}", ruleSetId);

        // Verify rule set exists
        ruleSetRepository.findByIdAndDeletedFalse(ruleSetId)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", ruleSetId));

        Page<Rule> rules = ruleRepository.findByRuleSetIdAndDeletedFalse(ruleSetId, pageable);
        return rules.map(ruleMapper::toDto);
    }

    /**
     * Validate DRL syntax without executing.
     */
    public void validate(Long id) {
        log.debug("Validating rule ID: {}", id);

        Rule rule = ruleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", id));

        if (rule.getContent() == null || rule.getContent().trim().isEmpty()) {
            throw new BusinessException("Rule content cannot be empty", "INVALID_RULE_CONTENT");
        }

        DroolsCompilationResult result = droolsRuleCompiler.validateDrl(rule.getContent());
        if (!result.isValid()) {
            throw new BusinessException("Invalid DRL syntax: " + result.joinedErrors(), "INVALID_DRL_SYNTAX");
        }

        log.info("Validated rule ID: {}", id);
    }

    /**
     * Source {@code DroolsService.compileRule} on write: fail the mutation when Kie reports ERROR.
     */
    private void assertCompiles(String content, String name) {
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("Rule content cannot be empty", "INVALID_RULE_CONTENT");
        }
        DroolsCompilationResult result = droolsRuleCompiler.validateDrl(content);
        if (!result.isValid()) {
            throw new BusinessException(
                    "Failed to compile rule: " + name + ": " + result.joinedErrors(),
                    "INVALID_DRL_SYNTAX"
            );
        }
    }

    /**
     * Analyze potential impact of a rule using ruleset and audit usage signals.
     */
    @Transactional(readOnly = true)
    public RuleImpactDto getImpact(Long id) {
        Rule rule = ruleRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rule", id));

        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(rule.getRuleSetId())
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", rule.getRuleSetId()));

        long totalRules = ruleRepository.countByRuleSetIdAndDeletedFalse(rule.getRuleSetId());
        long activeRules = ruleRepository.countByRuleSetIdAndStatusAndDeletedFalse(rule.getRuleSetId(), RuleStatus.ACTIVE);
        long executions = pricingAuditLogRepository.countByRuleSetId(rule.getRuleSetId());
        long executionsLast30Days = pricingAuditLogRepository.countByRuleSetIdAndExecutedAtGreaterThanEqual(
                rule.getRuleSetId(),
                Instant.now().minusSeconds(30L * 24 * 60 * 60)
        );

        return RuleImpactDto.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .ruleType(rule.getRuleType())
                .ruleStatus(rule.getStatus())
                .priority(rule.getPriority())
                .ruleSetId(ruleSet.getId())
                .ruleSetStatus(ruleSet.getStatus())
                .activeRuleSet(ruleSet.getStatus() == RuleSetStatus.ACTIVE)
                .totalRulesInRuleSet(totalRules)
                .activeRulesInRuleSet(activeRules)
                .pricingExecutionsWithRuleSet(executions)
                .pricingExecutionsWithRuleSetLast30Days(executionsLast30Days)
                .build();
    }
}
