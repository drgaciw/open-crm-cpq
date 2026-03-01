package com.cpq.shared.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Base entity for Transactional Outbox pattern.
 * All microservices should extend this for reliable event publishing.
 */
@Entity
@Table(name = "outbox")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "event_type")
public abstract class TransactionalOutbox {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "event_payload", nullable = false, columnDefinition = "TEXT")
    private String eventPayload;
    
    @Column(name = "event_type", nullable = false, insertable = false, updatable = false)
    private String eventType;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "retries", nullable = false)
    private int retries = 0;
    
    @Column(name = "locked_until")
    private Instant lockedUntil;
    
    protected TransactionalOutbox() {
        // Default constructor for JPA
    }
    
    protected TransactionalOutbox(String eventPayload, String eventType) {
        this.eventPayload = eventPayload;
        this.eventType = eventType;
        this.createdAt = Instant.now();
    }
    
    // Getters and setters
    
    public UUID getId() {
        return id;
    }
    
    public String getEventPayload() {
        return eventPayload;
    }
    
    public void setEventPayload(String eventPayload) {
        this.eventPayload = eventPayload;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public Instant getProcessedAt() {
        return processedAt;
    }
    
    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }
    
    public int getRetries() {
        return retries;
    }
    
    public void incrementRetries() {
        this.retries++;
    }
    
    public Instant getLockedUntil() {
        return lockedUntil;
    }
    
    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }
    
    public boolean isProcessed() {
        return processedAt != null;
    }
    
    public boolean isReadyToProcess(Instant now) {
        return !isProcessed() && (lockedUntil == null || lockedUntil.isBefore(now));
    }
}