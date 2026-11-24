package com.opencrm.quote.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findByIdAndDeletedFalse(Long id);

    List<ApprovalRequest> findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(Long quoteId);

    List<ApprovalRequest> findByStatusAndDeletedFalse(ApprovalStatus status);

    Optional<ApprovalRequest> findFirstByQuoteIdAndStatusAndDeletedFalse(Long quoteId, ApprovalStatus status);

    /**
     * Legacy local rows are visible only through the owning quote's tenant, and through its customer
     * when the caller is account-scoped. A null account does not filter.
     */
    @Query("""
            SELECT a FROM ApprovalRequest a, com.opencrm.quote.quote.Quote q
            WHERE a.id = :id
              AND a.quoteId = q.id
              AND a.deleted = false
              AND q.deleted = false
              AND q.tenantId = :tenantId
              AND (:accountId IS NULL OR q.customerExternalId = :accountId)
            """)
    Optional<ApprovalRequest> findVisibleById(@Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("accountId") String accountId);

    @Query("""
            SELECT a FROM ApprovalRequest a, com.opencrm.quote.quote.Quote q
            WHERE a.quoteId = q.id
              AND a.deleted = false
              AND q.deleted = false
              AND a.status = :status
              AND q.tenantId = :tenantId
              AND (:accountId IS NULL OR q.customerExternalId = :accountId)
            """)
    List<ApprovalRequest> findByStatusForTenant(@Param("status") ApprovalStatus status,
            @Param("tenantId") String tenantId,
            @Param("accountId") String accountId);
}
