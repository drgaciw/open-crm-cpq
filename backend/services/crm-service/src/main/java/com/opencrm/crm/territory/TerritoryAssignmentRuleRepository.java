package com.opencrm.crm.territory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Rules are loaded with static JPQL. Callers do not concatenate criteria into the query.
 */
@Repository
public interface TerritoryAssignmentRuleRepository extends JpaRepository<TerritoryAssignmentRule, Long> {

    @Query("""
            SELECT r FROM TerritoryAssignmentRule r
            WHERE r.deleted = false
              AND r.active = true
            ORDER BY r.priority ASC, r.id ASC
            """)
    List<TerritoryAssignmentRule> findApplicable();

    List<TerritoryAssignmentRule> findByDeletedFalseOrderByPriorityAscIdAsc();
}
