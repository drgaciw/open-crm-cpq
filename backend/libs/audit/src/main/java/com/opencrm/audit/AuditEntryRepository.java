package com.opencrm.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Append-only access to {@link AuditEntry}. Deliberately extends the bare Spring Data {@link Repository} marker
 * interface, not {@code JpaRepository} or {@code CrudRepository}: those expose {@code delete}/{@code deleteAll}
 * methods that would let application code try to mutate the trail even though the entity and the database both
 * refuse it. This interface declares only what {@link AuditTrailService} needs: write one entry, and read.
 */
interface AuditEntryRepository extends Repository<AuditEntry, Long> {

    AuditEntry save(AuditEntry entry);

    /** The current head of the chain (highest id), to read its {@code entryHash} as the next entry's
     * {@code previousHash}. Empty for a brand-new, still-empty trail. */
    Optional<AuditEntry> findTopByOrderByIdDesc();

    /** Every entry in chain order, oldest first. Used by {@link AuditTrailService#verifyChainIntegrity()}, a
     * maintenance/verification operation — not a hot path, so a full scan is acceptable here. */
    List<AuditEntry> findAllByOrderByIdAsc();

    /**
     * SearchAuditTrail: filter by tenant, aggregate (type and/or id), principal and an occurred-at time range.
     * Every filter is optional; an absent ({@code null}) parameter matches every row for that column.
     */
    @Query("""
            SELECT a FROM AuditEntry a
            WHERE (:tenantId IS NULL OR a.tenantId = :tenantId)
              AND (:aggregateType IS NULL OR a.aggregateType = :aggregateType)
              AND (:aggregateId IS NULL OR a.aggregateId = :aggregateId)
              AND (:principal IS NULL OR a.principal = :principal)
              AND (:from IS NULL OR a.occurredAt >= :from)
              AND (:to IS NULL OR a.occurredAt <= :to)
            ORDER BY a.id DESC
            """)
    Page<AuditEntry> search(@Param("tenantId") String tenantId,
                             @Param("aggregateType") String aggregateType,
                             @Param("aggregateId") String aggregateId,
                             @Param("principal") String principal,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}
