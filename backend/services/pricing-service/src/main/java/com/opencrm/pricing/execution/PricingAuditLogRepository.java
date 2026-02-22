package com.opencrm.pricing.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for PricingAuditLog entity.
 */
@Repository
public interface PricingAuditLogRepository extends JpaRepository<PricingAuditLog, Long> {

    /**
     * Find audit logs by quote external ID, ordered by execution time descending.
     */
    List<PricingAuditLog> findByQuoteExternalIdOrderByExecutedAtDesc(String quoteExternalId);

    /**
     * Find audit logs by customer external ID, ordered by execution time descending.
     */
    List<PricingAuditLog> findByCustomerExternalIdOrderByExecutedAtDesc(String customerExternalId);

    /**
     * Count audit logs by rule set ID.
     */
    long countByRuleSetId(Long ruleSetId);

    /**
     * Count audit logs by rule set ID executed after a timestamp.
     */
    long countByRuleSetIdAndExecutedAtGreaterThanEqual(Long ruleSetId, Instant executedAt);
}
