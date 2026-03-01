package com.opencrm.common.event;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Transactional Outbox pattern implementation.
 * Events are written to this table in the same transaction as domain changes,
 * then asynchronously published to Kafka by a separate relay process.
 *
 * This ensures at-least-once delivery semantics and maintains transactional consistency.
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_published", columnList = "published"),
    @Index(name = "idx_outbox_event_type", columnList = "event_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class OutboxEvent extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published", nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    public void markAsPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public void incrementRetryCount(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
    }
}
