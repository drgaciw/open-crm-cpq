package com.opencrm.crm.lead;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for Lead management.
 */
@RestController
@RequestMapping("/api/v1/leads")
@RequiredArgsConstructor
@Tag(name = "Leads", description = "Lead management endpoints")
public class LeadController {

    private final LeadService leadService;

    /**
     * Create a new lead.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new lead", description = "Creates a new lead in the system")
    public ApiResponse<LeadDto> create(@Valid @RequestBody LeadCreateRequest request) {
        LeadDto lead = leadService.create(request);
        return ApiResponse.success(lead, "Lead created successfully");
    }

    /**
     * List all leads with optional filters.
     */
    @GetMapping
    @Operation(summary = "List leads", description = "Get a paginated list of leads with optional filtering")
    public ApiResponse<List<LeadDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) LeadPriority priority) {
        Page<LeadDto> leads = leadService.list(pageable, status, priority);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(leads.getNumber())
                .size(leads.getSize())
                .totalElements(leads.getTotalElements())
                .totalPages(leads.getTotalPages())
                .hasNext(leads.hasNext())
                .hasPrevious(leads.hasPrevious())
                .build();

        return ApiResponse.success(leads.getContent(), pageInfo);
    }

    /**
     * Get a lead by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get lead by ID", description = "Retrieve a single lead by its ID")
    public ApiResponse<LeadDto> getById(@PathVariable Long id) {
        LeadDto lead = leadService.getById(id);
        return ApiResponse.success(lead);
    }

    /**
     * Update a lead.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update lead", description = "Update an existing lead")
    public ApiResponse<LeadDto> update(
            @PathVariable Long id,
            @Valid @RequestBody LeadUpdateRequest request) {
        LeadDto lead = leadService.update(id, request);
        return ApiResponse.success(lead, "Lead updated successfully");
    }

    /**
     * Delete a lead (soft delete).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete lead", description = "Soft delete a lead")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        leadService.delete(id);
        return ApiResponse.success(null, "Deleted successfully");
    }

    /**
     * Search leads by title.
     */
    @GetMapping("/search")
    @Operation(summary = "Search leads", description = "Search leads by title")
    public ApiResponse<Page<LeadDto>> search(
            @RequestParam("q") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<LeadDto> leads = leadService.search(query, pageable);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(leads.getNumber())
                .size(leads.getSize())
                .totalElements(leads.getTotalElements())
                .totalPages(leads.getTotalPages())
                .hasNext(leads.hasNext())
                .hasPrevious(leads.hasPrevious())
                .build();

        return ApiResponse.success(leads, pageInfo);
    }

    /**
     * Mark lead as contacted.
     */
    @PostMapping("/{id}/contact")
    @Operation(summary = "Mark lead as contacted", description = "Update lead status to contacted")
    public ApiResponse<LeadDto> markContacted(@PathVariable Long id) {
        LeadDto lead = leadService.markContacted(id);
        return ApiResponse.success(lead, "Lead marked as contacted");
    }

    /**
     * Qualify a lead.
     */
    @PostMapping("/{id}/qualify")
    @Operation(summary = "Qualify lead", description = "Mark lead as qualified")
    public ApiResponse<LeadDto> qualify(@PathVariable Long id) {
        LeadDto lead = leadService.qualify(id);
        return ApiResponse.success(lead, "Lead qualified successfully");
    }

    /**
     * Convert lead to deal.
     */
    @PostMapping("/{id}/convert")
    @Operation(summary = "Convert lead to deal", description = "Convert a qualified lead into a deal")
    public ApiResponse<LeadDto> convert(@PathVariable Long id) {
        LeadDto lead = leadService.convert(id);
        return ApiResponse.success(lead, "Lead converted to deal successfully");
    }

    /**
     * Mark lead as lost.
     */
    @PostMapping("/{id}/lose")
    @Operation(summary = "Mark lead as lost", description = "Mark lead as lost with a reason")
    public ApiResponse<LeadDto> lose(
            @PathVariable Long id,
            @Valid @RequestBody LeadLoseRequest request) {
        LeadDto lead = leadService.lose(id, request);
        return ApiResponse.success(lead, "Lead marked as lost");
    }

    /**
     * Get lead status counts for dashboard.
     */
    @GetMapping("/stats")
    @Operation(summary = "Get lead statistics", description = "Get count of leads by status for dashboard")
    public ApiResponse<Map<LeadStatus, Long>> getStatusCounts() {
        Map<LeadStatus, Long> stats = leadService.getStatusCounts();
        return ApiResponse.success(stats);
    }
}
