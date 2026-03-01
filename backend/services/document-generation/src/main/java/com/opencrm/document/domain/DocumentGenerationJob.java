package com.opencrm.document.domain;

import com.opencrm.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Document generation job entity.
 * Jobs are idempotent and non-blocking to request paths.
 */
@Entity
@Table(name = "document_generation_jobs", indexes = {
    @Index(name = "idx_doc_job_quote", columnList = "quote_id"),
    @Index(name = "idx_doc_job_status", columnList = "status"),
    @Index(name = "idx_doc_job_idempotency", columnList = "idempotency_key", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class DocumentGenerationJob extends BaseEntity {
    
    @Column(name = "quote_id", nullable = false)
    private String quoteId;
    
    @Column(name = "customer_id", nullable = false)
    private String customerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private JobStatus status = JobStatus.PENDING;
    
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
    
    @Column(name = "document_url")
    private String documentUrl;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "failed_at")
    private Instant failedAt;
    
    public void start() {
        this.status = JobStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }
    
    public void complete(String documentUrl) {
        this.status = JobStatus.COMPLETED;
        this.documentUrl = documentUrl;
        this.completedAt = Instant.now();
    }
    
    public void fail(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.failedAt = Instant.now();
        this.retryCount++;
    }
}
