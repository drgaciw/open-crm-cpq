package com.opencrm.document.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.DocumentType;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import com.opencrm.document.service.DocumentGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedEventConsumerTest {

    @Mock
    private DocumentGenerationJobRepository jobRepository;

    @Mock
    private DocumentGenerationService documentGenerationService;

    @InjectMocks
    private QuoteAcceptedEventConsumer consumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new QuoteAcceptedEventConsumer(jobRepository, documentGenerationService, objectMapper);
    }

    @Test
    void createsIdempotentJobForPublishedLanguageEvent() throws Exception {
        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder()
                        .quoteId("quote-uuid-1")
                        .quoteNumber("Q-100")
                        .acceptedAt(Instant.parse("2026-05-16T00:00:00Z"))
                        .build(),
                QuoteAcceptedEvent.CustomerSnapshot.builder().customerId("cust-1").build(),
                QuoteAcceptedEvent.PricingSnapshot.builder()
                        .totalAmount(new BigDecimal("1000.00"))
                        .currencyCode("USD")
                        .build(),
                null,
                null,
                QuoteAcceptedEvent.CorrelationSnapshot.builder().correlationId("corr-1").build()
        );
        event.setEventType(EventContractRegistry.QUOTE_ACCEPTED_V1.eventType());
        event.setEventVersion(EventContractRegistry.QUOTE_ACCEPTED_V1.eventVersion());

        when(jobRepository.existsByIdempotencyKey("quote-quote-uuid-1-corr-corr-1-pdf")).thenReturn(false);
        when(jobRepository.save(any(DocumentGenerationJob.class))).thenAnswer(invocation -> {
            DocumentGenerationJob job = invocation.getArgument(0);
            job.setId(42L);
            return job;
        });

        consumer.handleQuoteAccepted(objectMapper.writeValueAsString(event));

        ArgumentCaptor<DocumentGenerationJob> captor = ArgumentCaptor.forClass(DocumentGenerationJob.class);
        verify(jobRepository).save(captor.capture());
        DocumentGenerationJob saved = captor.getValue();
        assertThat(saved.getQuoteId()).isEqualTo("quote-uuid-1");
        assertThat(saved.getCustomerId()).isEqualTo("cust-1");
        assertThat(saved.getDocumentType()).isEqualTo(DocumentType.QUOTE_PDF);
        assertThat(saved.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(saved.getIdempotencyKey()).isEqualTo("quote-quote-uuid-1-corr-corr-1-pdf");
        verify(documentGenerationService).generateDocumentAsync(42L);
    }

    @Test
    void skipsDuplicateDelivery() throws Exception {
        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder().quoteId("quote-uuid-2").build(),
                null,
                null,
                null,
                null,
                null
        );

        when(jobRepository.existsByIdempotencyKey("quote-quote-uuid-2-pdf")).thenReturn(true);

        consumer.handleQuoteAccepted(objectMapper.writeValueAsString(event));

        verify(jobRepository, never()).save(any());
        verify(documentGenerationService, never()).generateDocumentAsync(anyLong());
    }
}
