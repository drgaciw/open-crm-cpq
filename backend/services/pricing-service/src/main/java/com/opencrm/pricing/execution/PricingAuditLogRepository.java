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
     * The most recent 20 cost-basis executions of a quote, newest first: the bounded candidate set for margin replay
     * (review finding m2). Only cost-basis executions write {@code waterfall_trace}, so the rest are skipped in SQL.
     */
    List<PricingAuditLog> findTop20ByQuoteExternalIdAndWaterfallTraceIsNotNullOrderByExecutedAtDescIdDesc(
            String quoteExternalId);

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
