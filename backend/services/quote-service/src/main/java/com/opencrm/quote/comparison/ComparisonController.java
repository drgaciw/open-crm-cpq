package com.opencrm.quote.comparison;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.quote.quote.QuoteDto;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
public class ComparisonController {
    private final ComparisonService service;

    @GetMapping("/compare")
    @Operation(summary = "Compare alternative quotes for one opportunity in the current tenant")
    public ApiResponse<QuoteComparisonDto> compare(@RequestParam String dealExternalId,
                                                  @RequestParam(required = false) List<Long> quoteIds) {
        return ApiResponse.success(service.compare(dealExternalId, quoteIds));
    }

    @PostMapping("/{id}/primary")
    @Operation(summary = "Replace the primary quote for this opportunity atomically")
    public ApiResponse<QuoteDto> markPrimary(@PathVariable Long id) {
        return ApiResponse.success(service.markPrimary(id), "Primary quote updated");
    }
}
