package com.opencrm.document.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.DocumentType;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import com.opencrm.document.service.DocumentGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Consumes quote accepted events and creates async document generation jobs.
 *
 * <p>Idempotent by nature (ADR-018 D6): one job per quote, keyed by the UNIQUE {@code idempotency_key}
 * {@code quote-<quoteId>-pdf}; a redelivery finds the job and returns. {@code QuoteAcceptedKafkaIntegrationTest} proves
 * it against a real broker. No inbox table is needed.
 *
 * <p>Generation starts <em>after</em> this transaction commits. It used to be started inside it, and since
 * {@code generateDocumentAsync} runs on another thread ({@code @EnableAsync}), that thread could look the job up before
 * the commit, fail to find it, and leave the job PENDING forever.
 *
 * <p>An unreadable payload is not retryable and goes to {@code cpq.quote.status.accepted.DLT} (D7).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuoteAcceptedEventConsumer {

    private final DocumentGenerationJobRepository jobRepository;
    private final DocumentGenerationService documentGenerationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.QUOTE_STATUS_ACCEPTED, groupId = "document-generation")
    @Transactional
    public void handleQuoteAccepted(String message) {
        QuoteAcceptedEvent event = parse(message);
        log.info("Received quote accepted event for quote: {}", event.getQuoteId());

        String idempotencyKey = "quote-" + event.getQuoteId() + "-pdf";
        if (jobRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Document generation job already exists for quote: {}", event.getQuoteId());
            return;
        }

        DocumentGenerationJob job = DocumentGenerationJob.builder()
                .quoteId(event.getQuoteId())
                .customerId(event.getCustomerId())
                .documentType(DocumentType.QUOTE_PDF)
                .status(JobStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .build();
        Long jobId = jobRepository.save(job).getId();

        afterCommit(() -> documentGenerationService.generateDocumentAsync(jobId));
        log.info("Created document generation job for quote: {}", event.getQuoteId());
    }

    private QuoteAcceptedEvent parse(String message) {
        try {
            QuoteAcceptedEvent event = objectMapper.readValue(message, QuoteAcceptedEvent.class);
            if (event == null || event.getQuoteId() == null || event.getQuoteId().isBlank()) {
                throw new NonRetryableEventException("Quote accepted event lacks quoteId: " + message);
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new NonRetryableEventException("Unreadable quote accepted event", e);
        }
    }

    /** Runs {@code action} once the current transaction commits (never on rollback); immediately if there is none. */
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
