package com.opencrm.pricing.openl;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for business-user maintained OpenL pricing tables.
 */
@RestController
@RequestMapping("/api/v1/openl-pricing-tables")
@RequiredArgsConstructor
@Tag(name = "OpenL Pricing Tables", description = "OpenL pricing and discount table import validation")
public class OpenLPricingTableImportController {

    private final OpenLPricingTableImportService importService;

    @PostMapping("/validate")
    @Operation(summary = "Validate OpenL pricing table import",
            description = "Preview-validates a CSV table before it is promoted into an OpenL-backed pricing rule set")
    public ApiResponse<OpenLPricingTableImportResult> validate(
            @Valid @RequestBody OpenLPricingTableImportRequest request) {
        return ApiResponse.success(importService.validate(request));
    }
}
