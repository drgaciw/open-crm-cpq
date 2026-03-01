package com.opencrm.document.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaTopics;
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

/**
 * Consumes quote accepted events and creates async document generation jobs.
 * Jobs are idempotent and non-blocking.
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
        try {
            QuoteAcceptedEvent event = objectMapper.readValue(message, QuoteAcceptedEvent.class);
            
            log.info("Received quote accepted event for quote: {}", event.getQuoteId());
            
            // Create idempotent job
            String idempotencyKey = "quote-" + event.getQuoteId() + "-pdf";
            
            // Check if job already exists
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
            
            jobRepository.save(job);
            
            // Trigger async document generation
            documentGenerationService.generateDocumentAsync(job.getId());
            
            log.info("Created document generation job for quote: {}", event.getQuoteId());
            
        } catch (Exception e) {
            log.error("Error processing quote accepted event", e);
            throw new RuntimeException("Failed to process quote accepted event", e);
        }
    }
}
