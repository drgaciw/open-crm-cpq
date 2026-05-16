package com.opencrm.document.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.DocumentType;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import com.opencrm.document.service.DocumentGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedEventConsumerTest {

    @Mock
    private DocumentGenerationJobRepository jobRepository;

    @Mock
    private DocumentGenerationService documentGenerationService;

    private ObjectMapper objectMapper;
    private QuoteAcceptedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        consumer = new QuoteAcceptedEventConsumer(jobRepository, documentGenerationService, objectMapper);
    }

    @Test
    @DisplayName("should create one accepted quote PDF job")
    void createsAcceptedQuoteDocumentJob() throws Exception {
        when(jobRepository.existsByIdempotencyKey("quote-Q-001-pdf")).thenReturn(false);
        when(jobRepository.saveAndFlush(any(DocumentGenerationJob.class))).thenAnswer(invocation -> {
            DocumentGenerationJob job = invocation.getArgument(0);
            job.setId(123L);
            return job;
        });

        consumer.handleQuoteAccepted(message("Q-001", "C-001"));

        ArgumentCaptor<DocumentGenerationJob> captor = ArgumentCaptor.forClass(DocumentGenerationJob.class);
        verify(jobRepository).saveAndFlush(captor.capture());
        DocumentGenerationJob savedJob = captor.getValue();
        assertEquals("Q-001", savedJob.getQuoteId());
        assertEquals("C-001", savedJob.getCustomerId());
        assertEquals(DocumentType.QUOTE_PDF, savedJob.getDocumentType());
        assertEquals(JobStatus.PENDING, savedJob.getStatus());
        assertEquals("quote-Q-001-pdf", savedJob.getIdempotencyKey());
        verify(documentGenerationService).generateDocumentAsync(123L);
    }

    @Test
    @DisplayName("should skip duplicate accepted quote events found before insert")
    void skipsExistingIdempotencyKey() throws Exception {
        when(jobRepository.existsByIdempotencyKey("quote-Q-001-pdf")).thenReturn(true);

        consumer.handleQuoteAccepted(message("Q-001", "C-001"));

        verify(jobRepository, never()).saveAndFlush(any(DocumentGenerationJob.class));
        verify(documentGenerationService, never()).generateDocumentAsync(any());
    }

    @Test
    @DisplayName("should skip duplicate accepted quote events rejected by unique key")
    void skipsConcurrentDuplicateIdempotencyKey() throws Exception {
        when(jobRepository.existsByIdempotencyKey("quote-Q-001-pdf")).thenReturn(false, true);
        when(jobRepository.saveAndFlush(any(DocumentGenerationJob.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        consumer.handleQuoteAccepted(message("Q-001", "C-001"));

        verify(documentGenerationService, never()).generateDocumentAsync(any());
    }

    @Test
    @DisplayName("should fail data integrity errors that are not duplicate accepted quote events")
    void failsNonDuplicateDataIntegrityError() throws Exception {
        when(jobRepository.existsByIdempotencyKey("quote-Q-001-pdf")).thenReturn(false);
        when(jobRepository.saveAndFlush(any(DocumentGenerationJob.class)))
                .thenThrow(new DataIntegrityViolationException("not duplicate"));

        assertThrows(RuntimeException.class, () -> consumer.handleQuoteAccepted(message("Q-001", "C-001")));

        verify(documentGenerationService, never()).generateDocumentAsync(any());
    }

    private String message(String quoteId, String customerId) throws Exception {
        QuoteAcceptedEvent event = new QuoteAcceptedEvent(
                quoteId,
                Instant.parse("2026-05-12T01:00:00Z"),
                customerId,
                BigDecimal.valueOf(1000),
                "USD"
        );
        return objectMapper.writeValueAsString(event);
    }
}
