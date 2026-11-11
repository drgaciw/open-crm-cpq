package com.opencrm.quote.comparison;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes/{id}")
@RequiredArgsConstructor
@Tag(name = "Quote comparison", description = "Persisted alternatives for one opportunity; primary is a seller preference")
public class QuoteComparisonController {
    private final QuoteComparisonService service;

    @GetMapping("/comparison")
    @Operation(summary = "Compare alternatives in the anchor quote's workforce partition and opportunity")
    public ApiResponse<QuoteComparisonDto> compare(@PathVariable Long id) {
        return ApiResponse.success(service.compare(id));
    }

    @PostMapping("/primary")
    @Operation(summary = "Prefer this alternative without changing quote lifecycle or forecast")
    public ApiResponse<QuoteComparisonDto> primary(@PathVariable Long id) {
        return ApiResponse.success(service.markPrimary(id), "Primary alternative updated");
    }
}
