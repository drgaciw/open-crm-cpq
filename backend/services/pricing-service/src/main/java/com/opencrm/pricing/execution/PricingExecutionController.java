package com.opencrm.pricing.execution;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.pricing.waterfall.PricingContext;
import com.opencrm.pricing.waterfall.PricingResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Pricing Execution.
 */
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
@Tag(name = "Pricing Execution", description = "Pricing execution and audit endpoints")
public class PricingExecutionController {

    private final PricingExecutionService pricingExecutionService;

    /**
     * Execute pricing calculation.
     */
    @PostMapping("/execute")
    @Operation(summary = "Execute pricing", description = "Execute pricing calculation with full audit trail")
    public ApiResponse<PricingResult> execute(@Valid @RequestBody PricingContext context) {
        PricingResult result = pricingExecutionService.execute(context);
        return ApiResponse.success(result, "Pricing executed successfully");
    }

    /**
     * Simulate pricing calculation without persisting audit log.
     */
    @PostMapping("/simulate")
    @Operation(summary = "Simulate pricing", description = "Simulate pricing calculation without persistence")
    public ApiResponse<PricingResult> simulate(@Valid @RequestBody PricingContext context) {
        PricingResult result = pricingExecutionService.simulate(context);
        return ApiResponse.success(result, "Pricing simulated successfully");
    }

    /**
     * Simulate pricing for multiple contexts in one request.
     */
    @PostMapping("/simulate/bulk")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Bulk simulate pricing", description = "Simulate pricing for multiple contexts")
    public ApiResponse<BulkPricingSimulationResponse> simulateBulk(
            @Valid @RequestBody BulkPricingSimulationRequest request) {
        BulkPricingSimulationResponse result = pricingExecutionService.simulateBulk(request.getContexts());
        return ApiResponse.success(result, "Bulk pricing simulation completed");
    }

    /**
     * Get audit trail for a quote.
     */
    @GetMapping("/audit/{quoteExternalId}")
    @Operation(summary = "Get audit trail", description = "Retrieve pricing audit trail for a quote")
    public ApiResponse<List<PricingAuditLog>> getAuditTrail(@PathVariable String quoteExternalId) {
        List<PricingAuditLog> auditLogs = pricingExecutionService.getAuditTrail(quoteExternalId);
        return ApiResponse.success(auditLogs);
    }
}
