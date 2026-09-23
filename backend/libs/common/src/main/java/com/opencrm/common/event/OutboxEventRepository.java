package com.opencrm.common.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Leases the next due batch for the relay (ADR-018 D3). Must run inside a transaction: the rows stay locked
     * until it commits.
     * <ul>
     *   <li>{@code FOR UPDATE SKIP LOCKED}: concurrent relays (replicas, or pricing and pricebook sharing the
     *       {@code pricing} schema) never lease the same row, and never wait on each other.</li>
     *   <li>Only the <em>oldest</em> unpublished row of each aggregate is eligible. A later row of the same aggregate
     *       waits until every earlier one is {@code PUBLISHED} or {@code SKIPPED}, so per-aggregate order holds across
     *       failures and across replicas. A {@code FAILED} row therefore blocks its aggregate until it is requeued or
     *       skipped. The two {@code <>} conditions (not {@code NOT IN}) keep the partial index on
     *       {@code status <> 'PUBLISHED'} usable.</li>
     *   <li>Rows backing off ({@code next_attempt_at} in the future) are not due.</li>
     * </ul>
     * Order within an aggregate follows {@code id}; writes to one aggregate are serialised by its own row lock, so
     * {@code id} order is commit order there.
     */
    @Query(nativeQuery = true, value = """
            SELECT e.* FROM {h-schema}outbox_events e
            WHERE e.status = 'PENDING'
              AND (e.next_attempt_at IS NULL OR e.next_attempt_at <= :now)
              AND NOT EXISTS (
                  SELECT 1 FROM {h-schema}outbox_events older
                  WHERE older.aggregate_type = e.aggregate_type
                    AND older.aggregate_id = e.aggregate_id
                    AND older.status <> 'PUBLISHED'
                    AND older.status <> 'SKIPPED'
                    AND older.id < e.id)
            ORDER BY e.id
            LIMIT :limit
            FOR UPDATE OF e SKIP LOCKED
            """)
    List<OutboxEvent> leaseDueBatch(@Param("now") Instant now, @Param("limit") int limit);

    @Query("SELECT e FROM OutboxEvent e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents();

    long countByStatus(OutboxStatus status);

    /** Creation time of the oldest row in {@code status}, or {@code null} if there is none. */
    @Query("SELECT MIN(e.createdAt) FROM OutboxEvent e WHERE e.status = :status")
    Instant findOldestCreatedAt(@Param("status") OutboxStatus status);

    /** Single idempotent delete (ADR-018 D3), so every replica can run the cleanup safely. */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.published = true AND e.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
