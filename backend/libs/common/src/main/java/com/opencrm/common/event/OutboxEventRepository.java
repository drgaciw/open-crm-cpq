package com.opencrm.common.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT e FROM OutboxEvent e WHERE e.published = false AND e.retryCount < :maxRetries ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnpublishedEventsWithRetryLimit(int maxRetries);

    @Query("SELECT e FROM OutboxEvent e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<OutboxEvent> findUnpublishedEvents();

    @Query("SELECT e FROM OutboxEvent e WHERE e.published = true AND e.publishedAt < :before")
    List<OutboxEvent> findPublishedEventsBefore(Instant before);
}
