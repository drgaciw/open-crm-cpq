package com.opencrm.crm.deal;

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

import java.util.List;

/**
 * REST controller for Deal management.
 */
@RestController
@RequestMapping("/api/v1/deals")
@RequiredArgsConstructor
@Tag(name = "Deals", description = "Deal management endpoints")
public class DealController {

    private final DealService dealService;

    /**
     * Create a new deal.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new deal", description = "Creates a new deal in the system")
    public ApiResponse<DealDto> create(@Valid @RequestBody DealCreateRequest request) {
        DealDto deal = dealService.create(request);
        return ApiResponse.success(deal, "Deal created successfully");
    }

    /**
     * List all deals with optional filters.
     */
    @GetMapping
    @Operation(summary = "List deals", description = "Get a paginated list of deals with optional filtering")
    public ApiResponse<List<DealDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) DealStage stage) {
        Page<DealDto> deals = dealService.list(pageable, stage);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(deals.getNumber())
                .size(deals.getSize())
                .totalElements(deals.getTotalElements())
                .totalPages(deals.getTotalPages())
                .hasNext(deals.hasNext())
                .hasPrevious(deals.hasPrevious())
                .build();

        return ApiResponse.success(deals.getContent(), pageInfo);
    }

    /**
     * Get a deal by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get deal by ID", description = "Retrieve a single deal by its ID")
    public ApiResponse<DealDto> getById(@PathVariable Long id) {
        DealDto deal = dealService.getById(id);
        return ApiResponse.success(deal);
    }

    /**
     * Update a deal.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update deal", description = "Update an existing deal")
    public ApiResponse<DealDto> update(
            @PathVariable Long id,
            @Valid @RequestBody DealUpdateRequest request) {
        DealDto deal = dealService.update(id, request);
        return ApiResponse.success(deal, "Deal updated successfully");
    }

    /**
     * Delete a deal (soft delete).
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete deal", description = "Soft delete a deal")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        dealService.delete(id);
        return ApiResponse.success(null, "Deleted successfully");
    }

    /**
     * Search deals by name.
     */
    @GetMapping("/search")
    @Operation(summary = "Search deals", description = "Search deals by name")
    public ApiResponse<Page<DealDto>> search(
            @RequestParam("q") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<DealDto> deals = dealService.search(query, pageable);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(deals.getNumber())
                .size(deals.getSize())
                .totalElements(deals.getTotalElements())
                .totalPages(deals.getTotalPages())
                .hasNext(deals.hasNext())
                .hasPrevious(deals.hasPrevious())
                .build();

        return ApiResponse.success(deals, pageInfo);
    }

    /**
     * Advance deal to next stage.
     */
    @PostMapping("/{id}/advance")
    @Operation(summary = "Advance deal stage", description = "Move deal to the next stage in the pipeline")
    public ApiResponse<DealDto> advanceStage(@PathVariable Long id) {
        DealDto deal = dealService.advanceStage(id);
        return ApiResponse.success(deal, "Deal advanced to next stage");
    }

    /**
     * Mark deal as won.
     */
    @PostMapping("/{id}/win")
    @Operation(summary = "Mark deal as won", description = "Mark a deal as won")
    public ApiResponse<DealDto> win(@PathVariable Long id) {
        DealDto deal = dealService.win(id);
        return ApiResponse.success(deal, "Deal marked as won");
    }

    /**
     * Mark deal as lost.
     */
    @PostMapping("/{id}/lose")
    @Operation(summary = "Mark deal as lost", description = "Mark deal as lost with a reason")
    public ApiResponse<DealDto> lose(
            @PathVariable Long id,
            @Valid @RequestBody DealLoseRequest request) {
        DealDto deal = dealService.lose(id, request);
        return ApiResponse.success(deal, "Deal marked as lost");
    }

    /**
     * Get pipeline summary for dashboard.
     */
    @GetMapping("/pipeline")
    @Operation(summary = "Get pipeline summary", description = "Get pipeline statistics grouped by stage")
    public ApiResponse<List<PipelineSummaryDto>> getPipelineSummary() {
        List<PipelineSummaryDto> summary = dealService.getPipelineSummary();
        return ApiResponse.success(summary);
    }
}
