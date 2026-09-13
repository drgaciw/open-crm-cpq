package com.opencrm.approval.controller;

import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.approval.service.ApprovalService;
import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for approval routing.
 * Risk evaluation and approval state machine for exceptions (margin/terms).
 * API boundary exposes {@link ApprovalRequestDto} only — never the JPA entity.
 */
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approval Routing", description = "Approval state machine and routing endpoints")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create/submit approval request (quote BFF / internal orchestration)")
    public ApiResponse<ApprovalRequestDto> submit(@Valid @RequestBody ApprovalSubmitRequest request) {
        ApprovalRequestDto created = approvalService.submit(request);
        return ApiResponse.success(created, "Approval request created");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approval request by ID")
    public ApiResponse<ApprovalRequestDto> getById(@PathVariable Long id) {
        return ApiResponse.success(approvalService.getById(id));
    }

    @GetMapping("/pending")
    @Operation(summary = "List all pending approval requests")
    public ApiResponse<List<ApprovalRequestDto>> getPendingApprovals() {
        return ApiResponse.success(approvalService.getPendingApprovals());
    }

    @GetMapping("/pending/tier/{tier}")
    @Operation(summary = "List pending approvals by tier")
    public ApiResponse<List<ApprovalRequestDto>> getPendingByTier(@PathVariable String tier) {
        return ApiResponse.success(approvalService.getPendingApprovalsByTier(tier));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending request")
    public ApiResponse<ApprovalRequestDto> approve(
            @PathVariable Long id,
            @RequestParam String approverId) {
        ApprovalRequestDto request = approvalService.approve(id, approverId);
        return ApiResponse.success(request, "Approval request approved");
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request")
    public ApiResponse<ApprovalRequestDto> reject(
            @PathVariable Long id,
            @RequestParam String approverId,
            @RequestParam String reason) {
        ApprovalRequestDto request = approvalService.reject(id, approverId, reason);
        return ApiResponse.success(request, "Approval request rejected");
    }
}
