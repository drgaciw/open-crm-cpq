package com.opencrm.approval.repository;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    List<ApprovalRequest> findByQuoteIdAndDeletedFalseOrderBySubmittedAtDescIdDesc(String quoteId);

    List<ApprovalRequest> findByQuoteIdAndStatus(String quoteId, ApprovalStatus status);

    List<ApprovalRequest> findByStatus(ApprovalStatus status);

    List<ApprovalRequest> findByApprovalTierAndStatus(String approvalTier, ApprovalStatus status);

    /**
     * A null {@code accountId} does not filter by account. A non-null account hides every other account,
     * including rows whose account is not known.
     */
    @Query("""
            SELECT a FROM ApprovalRequest a
            WHERE a.id = :id
              AND a.tenantId = :tenantId
              AND (:accountId IS NULL OR a.accountId = :accountId)
            """)
    Optional<ApprovalRequest> findScopedById(@Param("id") Long id,
            @Param("tenantId") String tenantId,
            @Param("accountId") String accountId);

    @Query("""
            SELECT a FROM ApprovalRequest a
            WHERE a.deleted = false
              AND a.quoteId = :quoteId
              AND a.tenantId = :tenantId
              AND (:accountId IS NULL OR a.accountId = :accountId)
            ORDER BY a.submittedAt DESC, a.id DESC
            """)
    List<ApprovalRequest> findScopedByQuoteId(@Param("quoteId") String quoteId,
            @Param("tenantId") String tenantId,
            @Param("accountId") String accountId);

    @Query("""
            SELECT a FROM ApprovalRequest a
            WHERE a.status = :status
              AND a.tenantId = :tenantId
              AND (:accountId IS NULL OR a.accountId = :accountId)
            """)
    List<ApprovalRequest> findScopedByStatus(@Param("status") ApprovalStatus status,
            @Param("tenantId") String tenantId,
            @Param("accountId") String accountId);

    @Query("""
            SELECT a FROM ApprovalRequest a
            WHERE a.approvalTier = :tier
              AND a.status = :status
              AND a.tenantId = :tenantId
              AND (:accountId IS NULL OR a.accountId = :accountId)
            """)
    List<ApprovalRequest> findScopedByTierAndStatus(@Param("tier") String tier,
            @Param("status") ApprovalStatus status,
            @Param("tenantId") String tenantId,
            @Param("accountId") String accountId);

    List<ApprovalRequest> findByQuoteIdAndQuoteVersionAndDeletedFalse(String quoteId, String quoteVersion);

    List<ApprovalRequest> findByQuoteIdAndDeletedFalseAndStatusIn(String quoteId, Collection<ApprovalStatus> statuses);

    /**
     * Pending rows this tenant may see: legacy rows with no tenant, plus rows stamped with this tenant.
     * Other tenants' matrix rows stay out of the list.
     */
    @Query("""
            SELECT a FROM ApprovalRequest a
            WHERE a.status = com.opencrm.approval.domain.ApprovalStatus.PENDING
              AND (a.tenantId IS NULL OR a.tenantId = :tenantId)
            """)
    List<ApprovalRequest> findPendingVisibleToTenant(@Param("tenantId") String tenantId);

    @Query("""
            SELECT a FROM ApprovalRequest a
            WHERE a.status = com.opencrm.approval.domain.ApprovalStatus.PENDING
              AND a.approvalTier = :tier
              AND (a.tenantId IS NULL OR a.tenantId = :tenantId)
            """)
    List<ApprovalRequest> findPendingVisibleToTenantAndTier(@Param("tenantId") String tenantId,
                                                            @Param("tier") String tier);
}
