package com.opencrm.pricing.ruleset;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.rule.Rule;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.rule.RuleStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Results;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing rule sets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RuleSetService {

    private final RuleSetRepository ruleSetRepository;
    private final RuleRepository ruleRepository;
    private final RuleSetMapper ruleSetMapper;

    /**
     * Create a new rule set.
     */
    public RuleSetDto create(RuleSetCreateRequest request) {
        log.debug("Creating rule set with name: {}", request.getName());

        RuleSet ruleSet = ruleSetMapper.toEntity(request);
        RuleSet saved = ruleSetRepository.save(ruleSet);
        log.info("Created rule set with ID: {}", saved.getId());

        return ruleSetMapper.toDto(saved);
    }

    /**
     * Get rule set by ID.
     */
    @Transactional(readOnly = true)
    public RuleSetDto getById(Long id) {
        log.debug("Fetching rule set by ID: {}", id);

        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", id));

        return ruleSetMapper.toDto(ruleSet);
    }

    /**
     * Update a rule set.
     */
    public RuleSetDto update(Long id, RuleSetUpdateRequest request) {
        log.debug("Updating rule set ID: {}", id);

        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", id));

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot update an ACTIVE rule set. Deprecate it first and create a new version.",
                    "INVALID_STATE_TRANSITION"
            );
        }

        ruleSetMapper.updateEntity(request, ruleSet);

        RuleSet updated = ruleSetRepository.save(ruleSet);
        log.info("Updated rule set ID: {}", updated.getId());

        return ruleSetMapper.toDto(updated);
    }

    /**
     * Soft delete a rule set.
     */
    public void delete(Long id) {
        log.debug("Deleting rule set ID: {}", id);

        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", id));

        if (ruleSet.getStatus() == RuleSetStatus.ACTIVE) {
            throw new BusinessException(
                    "Cannot delete an ACTIVE rule set. Deprecate it first.",
                    "INVALID_STATE_TRANSITION"
            );
        }

        ruleSet.softDelete();
        ruleSetRepository.save(ruleSet);

        log.info("Deleted rule set ID: {}", id);
    }

    /**
     * List rule sets with optional filtering.
     */
    @Transactional(readOnly = true)
    public Page<RuleSetDto> list(Pageable pageable, RuleSetStatus status) {
        log.debug("Listing rule sets with status: {}", status);

        Page<RuleSet> ruleSets;

        if (status != null) {
            ruleSets = ruleSetRepository.findByStatusAndDeletedFalse(status, pageable);
        } else {
            ruleSets = ruleSetRepository.findByDeletedFalse(pageable);
        }

        return ruleSets.map(ruleSetMapper::toDto);
    }

    /**
     * Promote a validated draft rule set to active production use.
     */
    @CacheEvict(value = "rules", allEntries = true)
    public RuleSetDto activate(Long id) {
        log.debug("Promoting rule set ID: {}", id);

        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", id));

        List<Rule> activeRules = ruleRepository.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(
                id, RuleStatus.ACTIVE);
        if (activeRules.isEmpty()) {
            throw new BusinessException(
                    "Cannot promote a rule set without at least one ACTIVE rule.",
                    "RULE_SET_NOT_VALIDATED"
            );
        }

        activeRules.forEach(this::validatePromotableRule);

        try {
            List<RuleSet> supersededRuleSets = ruleSetRepository.findByStatusAndDeletedFalse(RuleSetStatus.ACTIVE).stream()
                    .filter(activeRuleSet -> !activeRuleSet.getId().equals(ruleSet.getId()))
                    .toList();
            supersededRuleSets.forEach(RuleSet::deprecate);
            ruleSetRepository.saveAll(supersededRuleSets);
            ruleSet.activate();
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage(), "INVALID_STATE_TRANSITION");
        }

        RuleSet updated = ruleSetRepository.save(ruleSet);
        log.info("Activated rule set ID: {}", id);

        return ruleSetMapper.toDto(updated);
    }

    private void validatePromotableRule(Rule rule) {
        if (rule.getContent() == null || rule.getContent().trim().isEmpty()) {
            throw new BusinessException(
                    "Cannot promote rule set because rule " + rule.getId() + " has empty content.",
                    "INVALID_RULE_CONTENT"
            );
        }

        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.write("src/main/resources/rule-" + rule.getId() + ".drl", rule.getContent());
        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs).buildAll();

        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            String errorMsg = results.getMessages(Message.Level.ERROR).stream()
                    .map(Message::getText)
                    .collect(Collectors.joining(", "));
            throw new BusinessException(
                    "Cannot promote rule set because rule " + rule.getId() + " has invalid DRL syntax: " + errorMsg,
                    "INVALID_DRL_SYNTAX"
            );
        }
    }

    /**
     * Deprecate a rule set.
     */
    @CacheEvict(value = "rules", allEntries = true)
    public RuleSetDto deprecate(Long id) {
        log.debug("Deprecating rule set ID: {}", id);

        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", id));

        try {
            ruleSet.deprecate();
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage(), "INVALID_STATE_TRANSITION");
        }

        RuleSet updated = ruleSetRepository.save(ruleSet);
        log.info("Deprecated rule set ID: {}", id);

        return ruleSetMapper.toDto(updated);
    }
}
