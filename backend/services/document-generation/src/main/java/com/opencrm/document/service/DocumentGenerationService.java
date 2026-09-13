package com.opencrm.document.service;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.dto.DocumentGenerationJobDto;
import com.opencrm.document.mapper.DocumentGenerationJobMapper;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Asynchronous document generation service.
 * Jobs are idempotent and non-blocking to request paths.
 * API boundary exposes {@link DocumentGenerationJobDto} only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentGenerationService {

    private final DocumentGenerationJobRepository jobRepository;
    private final DocumentGenerationJobMapper jobMapper;

    @Transactional(readOnly = true)
    public DocumentGenerationJobDto getJobById(Long id) {
        return jobMapper.toDto(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentGenerationJobDto> getJobsByQuote(String quoteId) {
        return jobMapper.toDtoList(jobRepository.findByQuoteId(quoteId));
    }

    @Transactional(readOnly = true)
    public List<DocumentGenerationJobDto> getJobsByStatus(JobStatus status) {
        return jobMapper.toDtoList(jobRepository.findByStatus(status));
    }

    /**
     * Re-queue a FAILED job as PENDING and kick async generation.
     */
    @Transactional
    public DocumentGenerationJobDto retryJob(Long id) {
        DocumentGenerationJob job = findEntityById(id);

        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalStateException("Can only retry FAILED jobs");
        }

        job.setStatus(JobStatus.PENDING);
        job.setErrorMessage(null);
        DocumentGenerationJob saved = jobRepository.save(job);

        generateDocumentAsync(saved.getId());

        return jobMapper.toDto(saved);
    }

    /**
     * Generate document asynchronously.
     * This method returns immediately and processes in background.
     */
    @Async
    @Transactional
    public void generateDocumentAsync(Long jobId) {
        try {
            DocumentGenerationJob job = jobRepository.findById(jobId)
                    .orElseThrow(() -> new ResourceNotFoundException("DocumentGenerationJob", jobId));

            log.info("Starting async document generation for job: {}", jobId);

            job.start();
            jobRepository.save(job);

            // Simulate document generation
            // In production, this would:
            // 1. Fetch quote data
            // 2. Render HTML template
            // 3. Convert to PDF using openhtmltopdf
            // 4. Upload to cloud storage
            // 5. Update job with document URL

            Thread.sleep(2000); // Simulate processing time

            String documentUrl = "https://storage.example.com/quotes/" + job.getQuoteId() + ".pdf";

            job.complete(documentUrl);
            jobRepository.save(job);

            log.info("Completed document generation for job: {}", jobId);

        } catch (Exception e) {
            log.error("Error generating document for job: {}", jobId, e);

            DocumentGenerationJob job = jobRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.fail(e.getMessage());
                jobRepository.save(job);
            }
        }
    }

    private DocumentGenerationJob findEntityById(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentGenerationJob", id));
    }
}
