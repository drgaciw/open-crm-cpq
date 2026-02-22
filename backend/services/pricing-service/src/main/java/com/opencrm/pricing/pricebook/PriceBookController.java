package com.opencrm.pricing.pricebook;

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

/**
 * REST controller for PriceBook and PriceBookEntry management.
 */
@RestController
@RequestMapping("/api/v1/price-books")
@RequiredArgsConstructor
@Tag(name = "Price Books", description = "Price book and price book entry management endpoints")
public class PriceBookController {

    private final PriceBookService priceBookService;

    /**
     * Create a new price book.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new price book", description = "Creates a new price book")
    public ApiResponse<PriceBookDto> create(@Valid @RequestBody PriceBookCreateRequest request) {
        PriceBookDto priceBook = priceBookService.create(request);
        return ApiResponse.success(priceBook, "Price book created successfully");
    }

    /**
     * List price books.
     */
    @GetMapping
    @Operation(summary = "List price books", description = "Get a paginated list of price books with optional filtering")
    public ApiResponse<Page<PriceBookDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String region) {
        Page<PriceBookDto> priceBooks = priceBookService.list(pageable, region);
        return ApiResponse.success(priceBooks, buildPageInfo(priceBooks));
    }

    /**
     * Get a price book by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get price book by ID", description = "Retrieve a single price book by ID")
    public ApiResponse<PriceBookDto> getById(@PathVariable Long id) {
        PriceBookDto priceBook = priceBookService.getById(id);
        return ApiResponse.success(priceBook);
    }

    /**
     * Update an existing price book.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update price book", description = "Update an existing price book")
    public ApiResponse<PriceBookDto> update(
            @PathVariable Long id,
            @Valid @RequestBody PriceBookUpdateRequest request) {
        PriceBookDto priceBook = priceBookService.update(id, request);
        return ApiResponse.success(priceBook, "Price book updated successfully");
    }

    /**
     * Soft delete a price book.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete price book", description = "Soft delete a price book")
    public void delete(@PathVariable Long id) {
        priceBookService.delete(id);
    }

    /**
     * Create a new price book entry.
     */
    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create price book entry", description = "Create a new entry in a price book")
    public ApiResponse<PriceBookEntryDto> createEntry(
            @PathVariable Long id,
            @Valid @RequestBody PriceBookEntryCreateRequest request) {
        PriceBookEntryDto entry = priceBookService.createEntry(id, request);
        return ApiResponse.success(entry, "Price book entry created successfully");
    }

    /**
     * List entries in a price book.
     */
    @GetMapping("/{id}/entries")
    @Operation(summary = "List price book entries", description = "Get a paginated list of entries for a price book")
    public ApiResponse<Page<PriceBookEntryDto>> listEntries(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<PriceBookEntryDto> entries = priceBookService.listEntries(id, pageable);
        return ApiResponse.success(entries, buildPageInfo(entries));
    }

    /**
     * Get a single entry in a price book.
     */
    @GetMapping("/{id}/entries/{entryId}")
    @Operation(summary = "Get price book entry", description = "Get a single entry by ID from a price book")
    public ApiResponse<PriceBookEntryDto> getEntry(@PathVariable Long id, @PathVariable Long entryId) {
        PriceBookEntryDto entry = priceBookService.getEntry(id, entryId);
        return ApiResponse.success(entry);
    }

    /**
     * Update an existing entry in a price book.
     */
    @PutMapping("/{id}/entries/{entryId}")
    @Operation(summary = "Update price book entry", description = "Update a price book entry")
    public ApiResponse<PriceBookEntryDto> updateEntry(
            @PathVariable Long id,
            @PathVariable Long entryId,
            @Valid @RequestBody PriceBookEntryUpdateRequest request) {
        PriceBookEntryDto entry = priceBookService.updateEntry(id, entryId, request);
        return ApiResponse.success(entry, "Price book entry updated successfully");
    }

    /**
     * Soft delete a price book entry.
     */
    @DeleteMapping("/{id}/entries/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete price book entry", description = "Soft delete an entry in a price book")
    public void deleteEntry(@PathVariable Long id, @PathVariable Long entryId) {
        priceBookService.deleteEntry(id, entryId);
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
