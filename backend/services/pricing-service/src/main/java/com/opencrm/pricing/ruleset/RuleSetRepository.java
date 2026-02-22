package com.opencrm.pricing.ruleset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for RuleSet entity.
 */
@Repository
public interface RuleSetRepository extends JpaRepository<RuleSet, Long> {

    /**
     * Find rule set by ID (excluding deleted).
     */
    Optional<RuleSet> findByIdAndDeletedFalse(Long id);

    /**
     * Find rule set by UUID (excluding deleted).
     */
    Optional<RuleSet> findByUuidAndDeletedFalse(String uuid);

    /**
     * Find all non-deleted rule sets.
     */
    Page<RuleSet> findByDeletedFalse(Pageable pageable);

    /**
     * Find rule sets by status (excluding deleted).
     */
    Page<RuleSet> findByStatusAndDeletedFalse(RuleSetStatus status, Pageable pageable);

    /**
     * Find active rule sets effective on a given date (excluding deleted).
     */
    List<RuleSet> findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(
            RuleSetStatus status, LocalDate date);
}
