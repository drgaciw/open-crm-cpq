package com.opencrm.document.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.document.domain.DocumentGenerationJob;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.repository.DocumentGenerationJobRepository;
import com.opencrm.document.service.DocumentGenerationService;
import com.opencrm.common.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for document generation.
 * Asynchronous document assembly and job status tracking.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Document Generation", description = "Async document generation and job tracking endpoints")
public class DocumentController {

    private final DocumentGenerationJobRepository jobRepository;
    private final DocumentGenerationService documentGenerationService;

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Get document generation job by ID")
    public ApiResponse<DocumentGenerationJob> getJobById(@PathVariable Long id) {
        DocumentGenerationJob job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentGenerationJob", id));
        return ApiResponse.success(job);
    }

    @GetMapping("/jobs/quote/{quoteId}")
    @Operation(summary = "List document generation jobs for a quote")
    public ApiResponse<List<DocumentGenerationJob>> getJobsByQuote(@PathVariable String quoteId) {
        return ApiResponse.success(jobRepository.findByQuoteId(quoteId));
    }

    @GetMapping("/jobs/status/{status}")
    @Operation(summary = "List document generation jobs by status")
    public ApiResponse<List<DocumentGenerationJob>> getJobsByStatus(@PathVariable JobStatus status) {
        return ApiResponse.success(jobRepository.findByStatus(status));
    }

    @PostMapping("/jobs/{id}/retry")
    @Operation(summary = "Retry a failed document generation job")
    public ApiResponse<DocumentGenerationJob> retryJob(@PathVariable Long id) {
        DocumentGenerationJob job = jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DocumentGenerationJob", id));

        if (job.getStatus() != JobStatus.FAILED) {
            throw new IllegalStateException("Can only retry FAILED jobs");
        }

        job.setStatus(JobStatus.PENDING);
        job.setErrorMessage(null);
        jobRepository.save(job);

        documentGenerationService.generateDocumentAsync(job.getId());

        return ApiResponse.success(job, "Job queued for retry");
    }
}
