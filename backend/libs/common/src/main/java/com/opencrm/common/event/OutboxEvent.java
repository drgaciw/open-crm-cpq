package com.opencrm.common.event;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Index(name = "idx_outbox_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent extends BaseEntity {
    
    @Column(nullable = false, length = 100)
    private String aggregateType;
    
    @Column(nullable = false, length = 100)
    private String aggregateId;
    
    @Column(nullable = false, length = 100)
    private String eventType;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column(nullable = false)
    private Boolean published = false;
    
    @Column
    private Instant publishedAt;
    
    @Column(length = 500)
    private String errorMessage;
    
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    public void markAsPublished() {
        this.published = true;
        this.publishedAt = Instant.now();
    }
    
    public void incrementRetryCount(String errorMessage) {
        this.retryCount++;
        this.errorMessage = errorMessage;
    }
}
