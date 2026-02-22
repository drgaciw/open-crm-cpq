package com.opencrm.quote.lineitem;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quotes/{quoteId}/line-items")
@RequiredArgsConstructor
@Tag(name = "Quote Line Items", description = "Line item management within quotes")
public class QuoteLineItemController {

    private final QuoteLineItemService lineItemService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a line item to a quote")
    public ApiResponse<QuoteLineItemDto> add(
            @PathVariable Long quoteId,
            @Valid @RequestBody LineItemCreateRequest request) {
        QuoteLineItemDto item = lineItemService.addLineItem(quoteId, request);
        return ApiResponse.success(item, "Line item added successfully");
    }

    @GetMapping
    @Operation(summary = "Get all line items for a quote")
    public ApiResponse<List<QuoteLineItemDto>> list(@PathVariable Long quoteId) {
        List<QuoteLineItemDto> items = lineItemService.getLineItems(quoteId);
        return ApiResponse.success(items);
    }

    @PutMapping("/{lineItemId}")
    @Operation(summary = "Update a line item")
    public ApiResponse<QuoteLineItemDto> update(
            @PathVariable Long quoteId,
            @PathVariable Long lineItemId,
            @Valid @RequestBody LineItemCreateRequest request) {
        QuoteLineItemDto item = lineItemService.updateLineItem(quoteId, lineItemId, request);
        return ApiResponse.success(item, "Line item updated successfully");
    }

    @DeleteMapping("/{lineItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a line item from a quote")
    public void remove(@PathVariable Long quoteId, @PathVariable Long lineItemId) {
        lineItemService.removeLineItem(quoteId, lineItemId);
    }
}
