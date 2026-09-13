package com.opencrm.document.service;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.DocumentType;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.dto.DocumentGenerationJobDto;
import com.opencrm.document.mapper.DocumentGenerationJobMapper;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentGenerationService - DTO + retry")
class DocumentGenerationServiceTest {

    @Mock
    private DocumentGenerationJobRepository jobRepository;

    @Mock
    private DocumentGenerationJobMapper jobMapper;

    @InjectMocks
    private DocumentGenerationService documentGenerationService;

    private DocumentGenerationJob failedJob;

    @BeforeEach
    void setUp() {
        failedJob = DocumentGenerationJob.builder()
                .quoteId("Q-001")
                .customerId("C-001")
                .documentType(DocumentType.QUOTE_PDF)
                .status(JobStatus.FAILED)
                .idempotencyKey("quote-Q-001-pdf")
                .errorMessage("Template rendering error")
                .retryCount(1)
                .build();
        failedJob.setId(1L);
    }

    @Test
    @DisplayName("getJobById returns DTO")
    void getJobByIdReturnsDto() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(failedJob));
        when(jobMapper.toDto(failedJob)).thenReturn(dto(failedJob));

        DocumentGenerationJobDto result = documentGenerationService.getJobById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Q-001", result.getQuoteId());
        assertEquals(JobStatus.FAILED, result.getStatus());
        assertEquals("Template rendering error", result.getErrorMessage());
    }

    @Test
    @DisplayName("getJobsByQuote and getJobsByStatus return DTO lists")
    void listMethodsReturnDtos() {
        when(jobRepository.findByQuoteId("Q-001")).thenReturn(List.of(failedJob));
        when(jobRepository.findByStatus(JobStatus.FAILED)).thenReturn(List.of(failedJob));
        when(jobMapper.toDtoList(List.of(failedJob))).thenReturn(List.of(dto(failedJob)));

        List<DocumentGenerationJobDto> byQuote = documentGenerationService.getJobsByQuote("Q-001");
        List<DocumentGenerationJobDto> byStatus = documentGenerationService.getJobsByStatus(JobStatus.FAILED);

        assertEquals(1, byQuote.size());
        assertEquals("Q-001", byQuote.getFirst().getQuoteId());
        assertEquals(1, byStatus.size());
        assertEquals(JobStatus.FAILED, byStatus.getFirst().getStatus());
    }

    @Test
    @DisplayName("retryJob sets PENDING, clears error, queues async, returns DTO")
    void retryJobQueuesPending() {
        when(jobRepository.findById(1L)).thenReturn(Optional.of(failedJob));
        when(jobRepository.save(any(DocumentGenerationJob.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobMapper.toDto(any(DocumentGenerationJob.class))).thenAnswer(inv -> dto(inv.getArgument(0)));

        DocumentGenerationService spy = spy(documentGenerationService);
        doNothing().when(spy).generateDocumentAsync(1L);

        DocumentGenerationJobDto result = spy.retryJob(1L);

        assertEquals(JobStatus.PENDING, failedJob.getStatus());
        assertNull(failedJob.getErrorMessage());
        assertEquals(JobStatus.PENDING, result.getStatus());
        verify(spy).generateDocumentAsync(1L);
        verify(jobRepository).save(failedJob);
    }

    @Test
    @DisplayName("retryJob rejects non-FAILED jobs")
    void retryJobRejectsNonFailed() {
        failedJob.setStatus(JobStatus.COMPLETED);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(failedJob));

        assertThrows(IllegalStateException.class, () -> documentGenerationService.retryJob(1L));
        verify(jobRepository, never()).save(any());
    }

    @Test
    @DisplayName("getJobById throws when missing")
    void getJobByIdNotFound() {
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> documentGenerationService.getJobById(99L));
    }

    private static DocumentGenerationJobDto dto(DocumentGenerationJob job) {
        return DocumentGenerationJobDto.builder()
                .id(job.getId())
                .quoteId(job.getQuoteId())
                .customerId(job.getCustomerId())
                .documentType(job.getDocumentType())
                .status(job.getStatus())
                .idempotencyKey(job.getIdempotencyKey())
                .documentUrl(job.getDocumentUrl())
                .errorMessage(job.getErrorMessage())
                .retryCount(job.getRetryCount())
                .build();
    }
}
