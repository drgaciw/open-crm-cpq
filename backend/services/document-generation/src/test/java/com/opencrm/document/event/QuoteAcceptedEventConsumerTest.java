package com.opencrm.document.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import com.opencrm.document.service.DocumentGenerationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("document QuoteAcceptedEventConsumer (ADR-018 D6/D7)")
class QuoteAcceptedEventConsumerTest {

    private static final String EVENT = "{\"quoteId\":\"Q-7\",\"customerId\":\"C-1\"}";

    @Mock
    private DocumentGenerationJobRepository jobRepository;

    @Mock
    private DocumentGenerationService documentGenerationService;

    private QuoteAcceptedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new QuoteAcceptedEventConsumer(jobRepository, documentGenerationService, new ObjectMapper());
        when(jobRepository.existsByIdempotencyKey("quote-Q-7-pdf")).thenReturn(false);
        when(jobRepository.save(any(DocumentGenerationJob.class))).thenAnswer(call -> {
            DocumentGenerationJob job = call.getArgument(0);
            job.setId(41L);
            return job;
        });
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("generation starts only after the listener transaction commits")
    void generationStartsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        consumer.handleQuoteAccepted(EVENT);

        verify(documentGenerationService, never()).generateDocumentAsync(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(documentGenerationService).generateDocumentAsync(41L);
    }

    @Test
    @DisplayName("a redelivered event finds the job and creates nothing")
    void redeliveryIsANoOp() {
        when(jobRepository.existsByIdempotencyKey("quote-Q-7-pdf")).thenReturn(true);

        consumer.handleQuoteAccepted(EVENT);

        verify(jobRepository, never()).save(any());
        verify(documentGenerationService, never()).generateDocumentAsync(any());
    }

    @Test
    @DisplayName("D7: an unreadable event is not retryable")
    void unreadableEventIsNotRetryable() {
        assertThatThrownBy(() -> consumer.handleQuoteAccepted("{not json"))
                .isInstanceOf(NonRetryableEventException.class);
        verify(jobRepository, never()).save(any());
    }
}
