package com.opencrm.document.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests for Document Generation Job entity.
 * Validates idempotent job lifecycle and state transitions.
 */
@DisplayName("Document Generation Job - Lifecycle")
class DocumentGenerationJobTest {

    private DocumentGenerationJob job;

    @BeforeEach
    void setUp() {
        job = DocumentGenerationJob.builder()
                .quoteId("Q-001")
                .customerId("C-001")
                .documentType(DocumentType.QUOTE_PDF)
                .status(JobStatus.PENDING)
                .idempotencyKey("quote-Q-001-pdf")
                .build();
    }

    @Test
    @DisplayName("should start a pending job")
    void startJob() {
        job.start();

        assertEquals(JobStatus.IN_PROGRESS, job.getStatus());
        assertNotNull(job.getStartedAt());
    }

    @Test
    @DisplayName("should complete a job with document URL")
    void completeJob() {
        job.start();
        job.complete("https://storage.example.com/doc.pdf");

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        assertEquals("https://storage.example.com/doc.pdf", job.getDocumentUrl());
        assertNotNull(job.getCompletedAt());
    }

    @Test
    @DisplayName("should fail a job with error message")
    void failJob() {
        job.start();
        job.fail("Template rendering error");

        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Template rendering error", job.getErrorMessage());
        assertNotNull(job.getFailedAt());
        assertEquals(1, job.getRetryCount());
    }

    @Test
    @DisplayName("should increment retry count on repeated failures")
    void incrementRetryCount() {
        job.start();
        job.fail("Error 1");
        assertEquals(1, job.getRetryCount());

        job.setStatus(JobStatus.PENDING);
        job.start();
        job.fail("Error 2");
        assertEquals(2, job.getRetryCount());
    }

    @Test
    @DisplayName("should have idempotency key set")
    void idempotencyKey() {
        assertEquals("quote-Q-001-pdf", job.getIdempotencyKey());
    }
}
