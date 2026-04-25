package com.opencrm.document.service;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.DocumentType;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Document Generation Service")
class DocumentGenerationServiceTest {

    @Mock
    private DocumentGenerationJobRepository jobRepository;

    @InjectMocks
    private DocumentGenerationService documentGenerationService;

    private DocumentGenerationJob job;

    @BeforeEach
    void setUp() {
        job = DocumentGenerationJob.builder()
                .id(1L)
                .quoteId("Q-001")
                .customerId("C-001")
                .documentType(DocumentType.QUOTE_PDF)
                .status(JobStatus.PENDING)
                .idempotencyKey("quote-Q-001-pdf")
                .build();
    }

    @Test
    @DisplayName("should handle failure during document generation")
    void generateDocumentAsync_FailurePath() {
        // Arrange
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        // Throw an exception on the first save (when starting the job),
        // but succeed on the second (during the catch block).
        when(jobRepository.save(any(DocumentGenerationJob.class)))
            .thenThrow(new RuntimeException("Simulated error"))
            .thenReturn(job);

        // Act
        documentGenerationService.generateDocumentAsync(1L);

        // Assert
        // We verify that the job fail method was invoked inside the catch block
        assertEquals(JobStatus.FAILED, job.getStatus());
        assertEquals("Simulated error", job.getErrorMessage());

        // We should verify save was called twice: once in the try block (which throws),
        // and once in the catch block to save the failed state.
        verify(jobRepository, times(2)).save(any(DocumentGenerationJob.class));
    }
}
