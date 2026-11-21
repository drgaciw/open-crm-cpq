package com.opencrm.pricing.metricshistory;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricing.waterfall.PricingContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Flagged PD2 metrics history. Default off. No snapshot scheduler.
 */
@RestController
@Tag(name = "Metrics history", description = "On-demand financial metrics snapshots and churn-risk lookup")
public class MetricsHistoryController {

    private final MetricsHistoryService historyService;
    private final boolean enabled;

    public MetricsHistoryController(
            MetricsHistoryService historyService,
            @Value("${opencrm.pricing.metrics-history.enabled:false}") boolean enabled) {
        this.historyService = historyService;
        this.enabled = enabled;
    }

    @PostMapping("/api/v1/pricing/metrics-history/{customerExternalId}/snapshot")
    @Operation(summary = "Create metrics snapshot", description = "Simulates pricing and stores MetricsCalculator outputs")
    public ApiResponse<MetricsSnapshotDto> snapshot(
            @PathVariable String customerExternalId,
            @Valid @RequestBody PricingContext context) {
        requireEnabled();
        return ApiResponse.success(
                historyService.snapshot(customerExternalId, context),
                "Metrics snapshot created");
    }

    @GetMapping("/api/v1/pricing/metrics-history/{customerExternalId}")
    @Operation(summary = "List metrics snapshots", description = "Look back N days (default 30)")
    public ApiResponse<List<MetricsSnapshotDto>> history(
            @PathVariable String customerExternalId,
            @RequestParam(defaultValue = "30") int days) {
        requireEnabled();
        return ApiResponse.success(historyService.history(customerExternalId, days));
    }

    @GetMapping("/api/v1/pricing/churn-risk/{customerExternalId}")
    @Operation(summary = "Latest churn risk", description = "Returns churnScore from the most recent snapshot")
    public ApiResponse<ChurnRiskDto> churnRisk(@PathVariable String customerExternalId) {
        requireEnabled();
        return ApiResponse.success(historyService.churnRisk(customerExternalId));
    }

    private void requireEnabled() {
        if (!enabled) {
            throw new BusinessException(
                    "Pricing metrics history is disabled. Set opencrm.pricing.metrics-history.enabled=true for local/offline use.",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "FEATURE_DISABLED");
        }
    }
}
