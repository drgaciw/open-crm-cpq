package com.opencrm.pricebook;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * DRG-431 / BC-03-01: DefineCostBook / PublishCostBookVersion / GetCost.
 */
@RestController
@RequestMapping("/api/v1/cost-books")
@RequiredArgsConstructor
@Tag(name = "Cost Books", description = "Cost book and cost book entry management endpoints")
public class CostBookController {

    private final CostBookService costBookService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new cost book", description = "DefineCostBook: creates a new draft cost book")
    public ApiResponse<CostBookDto> create(@Valid @RequestBody CostBookCreateRequest request) {
        CostBookDto costBook = costBookService.create(request);
        return ApiResponse.success(costBook, "Cost book created successfully");
    }

    @GetMapping
    @Operation(summary = "List cost books", description = "Get a paginated list of cost books")
    public ApiResponse<Page<CostBookDto>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<CostBookDto> costBooks = costBookService.list(pageable);
        return ApiResponse.success(costBooks, buildPageInfo(costBooks));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get cost book by ID", description = "Retrieve a single cost book by ID")
    public ApiResponse<CostBookDto> getById(@PathVariable Long id) {
        return ApiResponse.success(costBookService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update cost book", description = "Update an existing draft cost book")
    public ApiResponse<CostBookDto> update(@PathVariable Long id, @Valid @RequestBody CostBookUpdateRequest request) {
        return ApiResponse.success(costBookService.update(id, request), "Cost book updated successfully");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete cost book", description = "Soft delete a draft cost book")
    public void delete(@PathVariable Long id) {
        costBookService.delete(id);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publish cost book version", description = "PublishCostBookVersion: makes this version immutable")
    public ApiResponse<CostBookDto> publish(@PathVariable Long id) {
        return ApiResponse.success(costBookService.publish(id), "Cost book published successfully");
    }

    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create cost book entry", description = "Create a new entry in a cost book")
    public ApiResponse<CostBookEntryDto> createEntry(
            @PathVariable Long id, @Valid @RequestBody CostBookEntryCreateRequest request) {
        return ApiResponse.success(costBookService.createEntry(id, request), "Cost book entry created successfully");
    }

    @GetMapping("/{id}/entries")
    @Operation(summary = "List cost book entries", description = "Get a paginated list of entries for a cost book")
    public ApiResponse<Page<CostBookEntryDto>> listEntries(
            @PathVariable Long id, @PageableDefault(size = 20) Pageable pageable) {
        Page<CostBookEntryDto> entries = costBookService.listEntries(id, pageable);
        return ApiResponse.success(entries, buildPageInfo(entries));
    }

    @GetMapping("/{id}/entries/{entryId}")
    @Operation(summary = "Get cost book entry", description = "Get a single entry by ID from a cost book")
    public ApiResponse<CostBookEntryDto> getEntry(@PathVariable Long id, @PathVariable Long entryId) {
        return ApiResponse.success(costBookService.getEntry(id, entryId));
    }

    @PutMapping("/{id}/entries/{entryId}")
    @Operation(summary = "Update cost book entry", description = "Update a cost book entry")
    public ApiResponse<CostBookEntryDto> updateEntry(
            @PathVariable Long id, @PathVariable Long entryId,
            @Valid @RequestBody CostBookEntryUpdateRequest request) {
        return ApiResponse.success(
                costBookService.updateEntry(id, entryId, request), "Cost book entry updated successfully");
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete cost book entry", description = "Soft delete an entry in a cost book")
    public void deleteEntry(@PathVariable Long id, @PathVariable Long entryId) {
        costBookService.deleteEntry(id, entryId);
    }

    @GetMapping("/{id}/cost")
    @Operation(summary = "GetCost", description = "Resolve the cost for a SKU, currency and unit of measure as of a date")
    public ApiResponse<PriceResolutionDto> getCost(
            @PathVariable Long id,
            @RequestParam String sku,
            @RequestParam String currency,
            @RequestParam(required = false, defaultValue = "EACH") String unitOfMeasure,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        return ApiResponse.success(costBookService.getCost(id, sku, currency, unitOfMeasure, asOfDate));
    }

    private ApiResponse.PageInfo buildPageInfo(Page<?> page) {
        return ApiResponse.PageInfo.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
