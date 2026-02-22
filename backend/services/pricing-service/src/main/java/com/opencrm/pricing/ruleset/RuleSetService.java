package com.opencrm.pricing.ruleset;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing rule sets.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class RuleSetService {

    private final RuleSetRepository ruleSetRepository;
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
     * Activate a rule set.
     */
    @CacheEvict(value = "rules", allEntries = true)
    public RuleSetDto activate(Long id) {
        log.debug("Activating rule set ID: {}", id);

        RuleSet ruleSet = ruleSetRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("RuleSet", id));

        try {
            ruleSet.activate();
        } catch (IllegalStateException e) {
            throw new BusinessException(e.getMessage(), "INVALID_STATE_TRANSITION");
        }

        RuleSet updated = ruleSetRepository.save(ruleSet);
        log.info("Activated rule set ID: {}", id);

        return ruleSetMapper.toDto(updated);
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
