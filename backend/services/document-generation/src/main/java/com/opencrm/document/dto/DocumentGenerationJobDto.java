package com.opencrm.document.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.document.domain.DocumentType;
import com.opencrm.document.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API response DTO for document generation jobs.
 * Omits BaseEntity {@code deleted} and Hibernate internals.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentGenerationJobDto {

    private Long id;
    private String uuid;
    private String quoteId;
    private String customerId;
    private DocumentType documentType;
    private JobStatus status;
    private String idempotencyKey;
    private String documentUrl;
    private String errorMessage;
    private Integer retryCount;
    private Instant startedAt;
    private Instant completedAt;
    private Instant failedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
