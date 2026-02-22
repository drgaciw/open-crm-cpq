package com.opencrm.pricing.rule;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Rule entity.
 */
@Repository
public interface RuleRepository extends JpaRepository<Rule, Long> {

    /**
     * Find rule by ID (excluding deleted).
     */
    Optional<Rule> findByIdAndDeletedFalse(Long id);

    /**
     * Find rules by rule set ID (excluding deleted).
     */
    Page<Rule> findByRuleSetIdAndDeletedFalse(Long ruleSetId, Pageable pageable);

    /**
     * Find rules by rule set ID and status (excluding deleted).
     */
    List<Rule> findByRuleSetIdAndStatusAndDeletedFalse(Long ruleSetId, RuleStatus status);

    /**
     * Find rules by rule set ID and status, ordered by priority descending (excluding deleted).
     */
    List<Rule> findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(Long ruleSetId, RuleStatus status);

    /**
     * Count all rules in a rule set (excluding deleted).
     */
    long countByRuleSetIdAndDeletedFalse(Long ruleSetId);

    /**
     * Count rules by rule set and status (excluding deleted).
     */
    long countByRuleSetIdAndStatusAndDeletedFalse(Long ruleSetId, RuleStatus status);
}
