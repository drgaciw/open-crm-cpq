package com.opencrm.approval.controller;

import com.opencrm.approval.domain.ApprovalRequest;
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
    public ApiResponse<ApprovalRequest> submit(@Valid @RequestBody ApprovalSubmitRequest request) {
        ApprovalRequest created = approvalService.submit(request);
        return ApiResponse.success(created, "Approval request created");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approval request by ID")
    public ApiResponse<ApprovalRequest> getById(@PathVariable Long id) {
        return ApiResponse.success(approvalService.getById(id));
    }

    @GetMapping("/pending")
    @Operation(summary = "List all pending approval requests")
    public ApiResponse<List<ApprovalRequest>> getPendingApprovals() {
        return ApiResponse.success(approvalService.getPendingApprovals());
    }

    @GetMapping("/pending/tier/{tier}")
    @Operation(summary = "List pending approvals by tier")
    public ApiResponse<List<ApprovalRequest>> getPendingByTier(@PathVariable String tier) {
        return ApiResponse.success(approvalService.getPendingApprovalsByTier(tier));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending request")
    public ApiResponse<ApprovalRequest> approve(
            @PathVariable Long id,
            @RequestParam String approverId) {
        ApprovalRequest request = approvalService.approve(id, approverId);
        return ApiResponse.success(request, "Approval request approved");
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request")
    public ApiResponse<ApprovalRequest> reject(
            @PathVariable Long id,
            @RequestParam String approverId,
            @RequestParam String reason) {
        ApprovalRequest request = approvalService.reject(id, approverId, reason);
        return ApiResponse.success(request, "Approval request rejected");
    }
}
