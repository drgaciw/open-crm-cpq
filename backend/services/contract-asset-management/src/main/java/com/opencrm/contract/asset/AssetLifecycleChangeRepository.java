package com.opencrm.contract.asset;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AssetLifecycleChangeRepository extends JpaRepository<AssetLifecycleChange, Long> {

    Optional<AssetLifecycleChange> findByDedupeKey(String dedupeKey);

    /**
     * Next monotonic order value. Gaps are fine; two concurrent enqueues never share a number.
     */
    @Query(value = "SELECT nextval('asset_lifecycle_change_seq')", nativeQuery = true)
    Long nextSequence();

    /**
     * Due pending rows, oldest effective date first, then sequence. {@code FOR UPDATE SKIP LOCKED}
     * so two runners never claim the same row and never wait on each other. Must run inside the
     * caller's transaction so the lock is held until that transaction ends.
     * <p>
     * Rows behind an earlier FAILED row on the same line are excluded <em>before</em> the limit, so a
     * failed line cannot fill every batch and starve due rows on other lines. An earlier PENDING row
     * cannot starve the batch: it sorts ahead of its successors in this same ordering, and the runner
     * still checks {@link #existsEarlierBlocking} per row (which also covers a predecessor that another
     * runner holds locked).
     */
    @Query(nativeQuery = true, value = """
            SELECT c.* FROM {h-schema}asset_lifecycle_changes c
            WHERE c.status = 'PENDING'
              AND c.is_deleted = false
              AND c.effective_date <= :today
              AND NOT EXISTS (
                  SELECT 1 FROM {h-schema}asset_lifecycle_changes f
                  WHERE f.subscription_id = c.subscription_id
                    AND f.contract_line_id = c.contract_line_id
                    AND f.is_deleted = false
                    AND f.status = 'FAILED'
                    AND (f.effective_date < c.effective_date
                         OR (f.effective_date = c.effective_date AND f.sequence_no < c.sequence_no)))
            ORDER BY c.effective_date, c.sequence_no
            LIMIT :limit
            FOR UPDATE OF c SKIP LOCKED
            """)
    List<AssetLifecycleChange> lockDue(@Param("today") LocalDate today, @Param("limit") int limit);

    /**
     * True when this line still has an earlier PENDING or FAILED row. A later due row waits.
     */
    @Query("""
            SELECT COUNT(c) FROM AssetLifecycleChange c
            WHERE c.subscriptionId = :subscriptionId
              AND c.contractLineId = :contractLineId
              AND c.deleted = false
              AND c.status IN (
                  com.opencrm.contract.asset.AssetLifecycleChangeStatus.PENDING,
                  com.opencrm.contract.asset.AssetLifecycleChangeStatus.FAILED)
              AND (c.effectiveDate < :effectiveDate
                   OR (c.effectiveDate = :effectiveDate AND c.sequenceNo < :sequenceNo))
            """)
    long countEarlierBlocking(@Param("subscriptionId") Long subscriptionId,
                               @Param("contractLineId") String contractLineId,
                               @Param("effectiveDate") LocalDate effectiveDate,
                               @Param("sequenceNo") long sequenceNo);

    default boolean existsEarlierBlocking(Long subscriptionId, String contractLineId,
                                          LocalDate effectiveDate, long sequenceNo) {
        return countEarlierBlocking(subscriptionId, contractLineId, effectiveDate, sequenceNo) > 0;
    }

    /** Effective date of the oldest pending row that is already due, or null when nothing is due. */
    @Query("""
            SELECT MIN(c.effectiveDate) FROM AssetLifecycleChange c
            WHERE c.status = com.opencrm.contract.asset.AssetLifecycleChangeStatus.PENDING
              AND c.deleted = false
              AND c.effectiveDate <= :today
            """)
    LocalDate findOldestDueDate(@Param("today") LocalDate today);
}
