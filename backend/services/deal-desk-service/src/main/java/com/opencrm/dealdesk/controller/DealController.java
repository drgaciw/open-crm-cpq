package com.opencrm.dealdesk.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.dealdesk.dto.ApproveDealRequestDto;
import com.opencrm.dealdesk.dto.CpqSyncResponseDto;
import com.opencrm.dealdesk.dto.DealRequestDto;
import com.opencrm.dealdesk.dto.DealResponseDto;
import com.opencrm.dealdesk.dto.RejectDealRequestDto;
import com.opencrm.dealdesk.model.DealStatus;
import com.opencrm.dealdesk.service.DealService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/deals")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DealResponseDto>>> listDeals(
            @RequestParam(required = false) DealStatus status,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) BigDecimal minValue,
            @RequestParam(required = false) String salesforceOpportunityId,
            @RequestParam(required = false) Boolean synced,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdAfter
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                dealService.getDeals(status, accountId, minValue, salesforceOpportunityId, synced, createdAfter)
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DealResponseDto>> getDeal(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(dealService.getDealById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DealResponseDto>> createDeal(@Valid @RequestBody DealRequestDto request) {
        DealResponseDto created = dealService.createDeal(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Deal created"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DealResponseDto>> updateDeal(
            @PathVariable Long id,
            @Valid @RequestBody DealRequestDto request
    ) {
        DealResponseDto updated = dealService.updateDeal(id, request);
        return ResponseEntity.ok(ApiResponse.success(updated, "Deal updated"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDeal(@PathVariable Long id) {
        dealService.deleteDeal(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Deal deleted"));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<DealResponseDto>> submitDeal(@PathVariable Long id) {
        DealResponseDto submitted = dealService.submitDeal(id);
        return ResponseEntity.ok(ApiResponse.success(submitted, "Deal submitted"));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<DealResponseDto>> approveDeal(
            @PathVariable Long id,
            @RequestBody(required = false) ApproveDealRequestDto request
    ) {
        String notes = request != null ? request.getNotes() : null;
        DealResponseDto approved = dealService.approveDeal(id, notes);
        return ResponseEntity.ok(ApiResponse.success(approved, "Deal approved"));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<DealResponseDto>> rejectDeal(
            @PathVariable Long id,
            @Valid @RequestBody RejectDealRequestDto request
    ) {
        DealResponseDto rejected = dealService.rejectDeal(id, request.getNotes());
        return ResponseEntity.ok(ApiResponse.success(rejected, "Deal rejected"));
    }

    @PostMapping("/{id}/recalculate-pricing")
    public ResponseEntity<ApiResponse<DealResponseDto>> recalculatePricing(@PathVariable Long id) {
        DealResponseDto recalculated = dealService.recalculatePricing(id);
        return ResponseEntity.ok(ApiResponse.success(recalculated, "Deal pricing recalculated"));
    }

    @PostMapping("/{id}/sync-pricing")
    public ResponseEntity<ApiResponse<DealResponseDto>> syncPricing(@PathVariable Long id) {
        DealResponseDto synced = dealService.syncPricing(id);
        return ResponseEntity.ok(ApiResponse.success(synced, "Deal pricing sync completed"));
    }

    @PostMapping("/{id}/cpq/quote/sync")
    public ResponseEntity<ApiResponse<CpqSyncResponseDto>> syncCpqQuote(
            @PathVariable Long id,
            @RequestParam(required = false) String quoteId
    ) {
        CpqSyncResponseDto synced = dealService.syncCpqQuote(id, quoteId);
        return ResponseEntity.ok(ApiResponse.success(synced, "Deal CPQ quote sync completed"));
    }

    @PostMapping("/{id}/cpq/price-rules/sync")
    public ResponseEntity<ApiResponse<CpqSyncResponseDto>> syncCpqPriceRules(
            @PathVariable Long id,
            @RequestParam(required = false) String quoteId,
            @RequestParam(required = false) String priceRuleId
    ) {
        CpqSyncResponseDto synced = dealService.syncCpqPriceRules(id, quoteId, priceRuleId);
        return ResponseEntity.ok(ApiResponse.success(synced, "Deal CPQ price rule sync completed"));
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ApiResponse<DealResponseDto>> syncDeal(@PathVariable Long id) {
        DealResponseDto synced = dealService.syncDeal(id);
        return ResponseEntity.ok(ApiResponse.success(synced, "Deal sync completed"));
    }

    @PostMapping("/batch-sync")
    public ResponseEntity<ApiResponse<List<DealResponseDto>>> batchSyncDeals(
            @RequestBody(required = false) List<Long> ids
    ) {
        List<DealResponseDto> synced = dealService.batchSyncDeals(ids);
        return ResponseEntity.ok(ApiResponse.success(synced, "Deal batch sync completed"));
    }
}
