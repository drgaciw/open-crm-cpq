package com.opencrm.contract.asset;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Renewal and churn reads. Active assets per account stay on
 * {@code GET /api/v1/assets?accountId&asOf}.
 */
@RestController
@RequestMapping("/api/v1/assets/reports")
@Tag(name = "Asset reports", description = "Upcoming renewals and churn for an account's installed base")
public class AssetReportController {

    private final AssetReportService reports;

    public AssetReportController(AssetReportService reports) {
        this.reports = reports;
    }

    /**
     * @param windowStart inclusive effective-end bound; pair with {@code windowEnd}
     * @param windowEnd inclusive effective-end bound
     * @param withinDays alternative to the explicit window, counted from {@code asOf}
     * @param asOf anchor date for {@code withinDays}; UTC today when omitted
     */
    @GetMapping("/upcoming-renewals")
    @Operation(summary = "Installed assets whose effective end falls in a window")
    public ApiResponse<UpcomingRenewalsReport> upcomingRenewals(
            @RequestParam String accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate windowStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate windowEnd,
            @RequestParam(required = false) Integer withinDays,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.success(reports.upcomingRenewals(accountId, windowStart, windowEnd, withinDays, asOf));
    }

    @GetMapping("/churn")
    @Operation(summary = "Terminated and downsized assets over a period; supersedes are excluded")
    public ApiResponse<AssetChurnReport> churn(
            @RequestParam String accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return ApiResponse.success(reports.churn(accountId, periodStart, periodEnd));
    }
}
