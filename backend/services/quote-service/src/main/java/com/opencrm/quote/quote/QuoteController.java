package com.opencrm.quote.quote;

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

@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Tag(name = "Quotes", description = "Quote lifecycle management endpoints")
public class QuoteController {

    private final QuoteService quoteService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new quote")
    public ApiResponse<QuoteDto> create(@Valid @RequestBody QuoteCreateRequest request) {
        QuoteDto quote = quoteService.create(request);
        return ApiResponse.success(quote, "Quote created successfully");
    }

    @GetMapping
    @Operation(summary = "List quotes with optional filters")
    public ApiResponse<Page<QuoteDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(required = false) DealType dealType,
            @RequestParam(required = false) String customerExternalId) {
        Page<QuoteDto> quotes = quoteService.list(pageable, status, dealType, customerExternalId);
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(quotes.getNumber())
                .size(quotes.getSize())
                .totalElements(quotes.getTotalElements())
                .totalPages(quotes.getTotalPages())
                .hasNext(quotes.hasNext())
                .hasPrevious(quotes.hasPrevious())
                .build();
        return ApiResponse.success(quotes, pageInfo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get quote by ID")
    public ApiResponse<QuoteDto> getById(@PathVariable Long id) {
        return ApiResponse.success(quoteService.getById(id));
    }

    @GetMapping("/number/{quoteNumber}")
    @Operation(summary = "Get quote by number")
    public ApiResponse<QuoteDto> getByQuoteNumber(@PathVariable String quoteNumber) {
        return ApiResponse.success(quoteService.getByQuoteNumber(quoteNumber));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a quote (only in DRAFT/REJECTED)")
    public ApiResponse<QuoteDto> update(@PathVariable Long id, @Valid @RequestBody QuoteUpdateRequest request) {
        return ApiResponse.success(quoteService.update(id, request), "Quote updated successfully");
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit quote for review")
    public ApiResponse<QuoteDto> submit(@PathVariable Long id) {
        return ApiResponse.success(quoteService.submitForReview(id), "Quote submitted for review");
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a quote")
    public ApiResponse<QuoteDto> approve(@PathVariable Long id) {
        return ApiResponse.success(quoteService.approve(id), "Quote approved");
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a quote")
    public ApiResponse<QuoteDto> reject(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return ApiResponse.success(quoteService.reject(id, reason), "Quote rejected");
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Send an approved quote")
    public ApiResponse<QuoteDto> send(@PathVariable Long id) {
        return ApiResponse.success(quoteService.send(id), "Quote sent");
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accept a sent quote")
    public ApiResponse<QuoteDto> accept(@PathVariable Long id) {
        return ApiResponse.success(quoteService.accept(id), "Quote accepted");
    }

    @PostMapping("/{id}/expire")
    @Operation(summary = "Expire a sent quote")
    public ApiResponse<QuoteDto> expire(@PathVariable Long id) {
        return ApiResponse.success(quoteService.expire(id), "Quote expired");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a quote")
    public ApiResponse<QuoteDto> cancel(@PathVariable Long id) {
        return ApiResponse.success(quoteService.cancel(id), "Quote cancelled");
    }

    @PostMapping("/{id}/clone")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Clone an existing quote")
    public ApiResponse<QuoteDto> cloneQuote(@PathVariable Long id) {
        return ApiResponse.success(quoteService.clone(id), "Quote cloned successfully");
    }

    @PostMapping("/{id}/calculate")
    @Operation(summary = "Calculate quote pricing")
    public ApiResponse<QuoteDto> calculate(@PathVariable Long id) {
        return ApiResponse.success(quoteService.calculate(id), "Quote calculated successfully");
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a quote")
    public void delete(@PathVariable Long id) {
        quoteService.delete(id);
    }
}
