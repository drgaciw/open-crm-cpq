package com.opencrm.document.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.document.domain.JobStatus;
import com.opencrm.document.dto.DocumentGenerationJobDto;
import com.opencrm.document.service.DocumentGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for document generation.
 * Asynchronous document assembly and job status tracking.
 * API boundary exposes {@link DocumentGenerationJobDto} only — never the JPA entity.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Document Generation", description = "Async document generation and job tracking endpoints")
public class DocumentController {

    private final DocumentGenerationService documentGenerationService;

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Get document generation job by ID")
    public ApiResponse<DocumentGenerationJobDto> getJobById(@PathVariable Long id) {
        return ApiResponse.success(documentGenerationService.getJobById(id));
    }

    @GetMapping("/jobs/quote/{quoteId}")
    @Operation(summary = "List document generation jobs for a quote")
    public ApiResponse<List<DocumentGenerationJobDto>> getJobsByQuote(@PathVariable String quoteId) {
        return ApiResponse.success(documentGenerationService.getJobsByQuote(quoteId));
    }

    @GetMapping("/jobs/status/{status}")
    @Operation(summary = "List document generation jobs by status")
    public ApiResponse<List<DocumentGenerationJobDto>> getJobsByStatus(@PathVariable JobStatus status) {
        return ApiResponse.success(documentGenerationService.getJobsByStatus(status));
    }

    @PostMapping("/jobs/{id}/retry")
    @Operation(summary = "Retry a failed document generation job")
    public ApiResponse<DocumentGenerationJobDto> retryJob(@PathVariable Long id) {
        DocumentGenerationJobDto job = documentGenerationService.retryJob(id);
        return ApiResponse.success(job, "Job queued for retry");
    }
}
