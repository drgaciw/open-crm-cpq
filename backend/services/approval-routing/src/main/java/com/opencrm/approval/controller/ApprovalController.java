package com.opencrm.approval.controller;

import com.opencrm.approval.dto.ApprovalCommentRequest;
import com.opencrm.approval.dto.ApprovalHistoryEntryDto;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.approval.service.ApprovalHistoryService;
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
 * <p>
 * No endpoint takes an identity: the requester and the approver are the authenticated caller (ADR-016 decision 6).
 */
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approval Routing", description = "Approval state machine and routing endpoints")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ApprovalHistoryService approvalHistoryService;
    private final com.opencrm.approval.service.IdempotentApprovalSubmission idempotentSubmission;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create/submit approval request (quote BFF / internal orchestration)")
    public ApiResponse<ApprovalRequestDto> submit(@Valid @RequestBody ApprovalSubmitRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        ApprovalRequestDto created = key == null ? approvalService.submit(request) : idempotentSubmission.submit(key, request);
        return ApiResponse.success(created, "Approval request created");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get approval request by ID")
    public ApiResponse<ApprovalRequestDto> getById(@PathVariable Long id) {
        return ApiResponse.success(approvalService.getById(id));
    }

    @GetMapping("/quote/{quoteId}")
    @Operation(summary = "List non-deleted approval history for a quote, newest submitted first")
    public ApiResponse<List<ApprovalRequestDto>> getByQuoteId(@PathVariable String quoteId) {
        return ApiResponse.success(approvalService.getByQuoteId(quoteId));
    }

    @GetMapping("/quote/{quoteId}/history")
    @Operation(summary = "GetApprovalHistory: decisions, escalations and comments for a quote, oldest first")
    public ApiResponse<List<ApprovalHistoryEntryDto>> getApprovalHistory(@PathVariable String quoteId) {
        return ApiResponse.success(approvalHistoryService.getApprovalHistory(quoteId));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "GetApprovalHistory for one approval case")
    public ApiResponse<List<ApprovalHistoryEntryDto>> getApprovalHistoryForCase(@PathVariable Long id) {
        return ApiResponse.success(approvalHistoryService.getApprovalHistoryForCase(id));
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a deal-desk comment or thread reply on an approval case")
    public ApiResponse<ApprovalHistoryEntryDto> addComment(
            @PathVariable Long id, @Valid @RequestBody ApprovalCommentRequest request) {
        return ApiResponse.success(approvalHistoryService.addComment(id, request), "Comment added");
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
    @Operation(summary = "Approve a pending request as the authenticated user (role must satisfy the tier)")
    public ApiResponse<ApprovalRequestDto> approve(
            @PathVariable Long id,
            @RequestParam(required = false) String comment,
            @RequestParam(required = false) Long quoteVersion) {
        ApprovalRequestDto request = approvalService.approve(id, comment, quoteVersion);
        return ApiResponse.success(request, "Approval request approved");
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending request as the authenticated user (role must satisfy the tier)")
    public ApiResponse<ApprovalRequestDto> reject(
            @PathVariable Long id,
            @RequestParam String reason,
            @RequestParam(required = false) Long quoteVersion) {
        ApprovalRequestDto request = approvalService.reject(id, reason, quoteVersion);
        return ApiResponse.success(request, "Approval request rejected");
    }

    @PostMapping("/{id}/escalate")
    @Operation(summary = "Escalate a pending request after its SLA window, along its escalation path")
    public ApiResponse<ApprovalRequestDto> escalate(
            @PathVariable Long id,
            @RequestParam(required = false) Long quoteVersion) {
        ApprovalRequestDto request = approvalService.escalate(id, quoteVersion);
        return ApiResponse.success(request, "Approval request escalated");
    }
}
