package com.opencrm.common.event;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
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

    /** Relay state (ADR-018 D2). {@link #published} is kept in step for existing readers. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    /** Earliest time the relay may retry this row; {@code null} means it is due now. */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    public void markAsPublished() {
        markAsPublished(Instant.now());
    }

    /** Records the broker acknowledgement. Only the relay calls this, and only after the send future completed. */
    public void markAsPublished(Instant acknowledgedAt) {
        this.published = true;
        this.publishedAt = acknowledgedAt;
        this.status = OutboxStatus.PUBLISHED;
        this.nextAttemptAt = null;
    }

    public void incrementRetryCount(String errorMessage) {
        this.retryCount++;
        this.errorMessage = truncate(errorMessage);
    }

    /**
     * Records a failed publish attempt (ADR-018 D2). The row stays unpublished. It becomes {@link OutboxStatus#FAILED}
     * once {@code maxAttempts} is reached; otherwise it is due again at {@code now + retryDelay}.
     *
     * @return {@code true} if this attempt exhausted the retry limit
     */
    public boolean recordFailedAttempt(String error, Instant now, int maxAttempts, Duration retryDelay) {
        incrementRetryCount(error);
        if (retryCount >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
            this.nextAttemptAt = null;
            return true;
        }
        this.nextAttemptAt = now.plus(retryDelay);
        return false;
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_LENGTH);
    }

    private static final int ERROR_MESSAGE_LENGTH = 500;
}
