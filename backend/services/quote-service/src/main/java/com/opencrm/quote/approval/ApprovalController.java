package com.opencrm.quote.approval;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Quote approval workflow endpoints")
public class ApprovalController {

    private final ApprovalService approvalService;

    @PostMapping("/{quoteId}/policy-evaluation")
    @Operation(summary = "Evaluate a quote version against commercial policy")
    public ApiResponse<QuotePolicyEvaluationResponse> evaluatePolicy(@PathVariable Long quoteId) {
        return ApiResponse.success(approvalService.evaluatePolicy(quoteId));
    }

    @PostMapping("/{quoteId}/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Submit a quote for approval")
    public ApiResponse<ApprovalRequestDto> submitForApproval(
            @PathVariable Long quoteId,
            @Valid @RequestBody ApprovalSubmitRequest request) {
        ApprovalRequestDto approval = approvalService.submitForApproval(quoteId, request);
        return ApiResponse.success(approval, "Submitted for approval");
    }

    @GetMapping("/{quoteId}/approvals")
    @Operation(summary = "Get approval history for a quote")
    public ApiResponse<List<ApprovalRequestDto>> getApprovals(@PathVariable Long quoteId) {
        List<ApprovalRequestDto> approvals = approvalService.getByQuoteId(quoteId);
        return ApiResponse.success(approvals);
    }

    @GetMapping("/{quoteId}/approvals/history")
    @Operation(summary = "GetApprovalHistory: decisions, escalations and comments, oldest first")
    public ApiResponse<List<ApprovalHistoryEntryDto>> getApprovalHistory(@PathVariable Long quoteId) {
        return ApiResponse.success(approvalService.getApprovalHistory(quoteId));
    }

    @PostMapping("/{quoteId}/approvals/{approvalId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a deal-desk comment or thread reply on an approval case")
    public ApiResponse<ApprovalHistoryEntryDto> addCaseComment(
            @PathVariable Long quoteId,
            @PathVariable Long approvalId,
            @Valid @RequestBody ApprovalCommentRequest request) {
        return ApiResponse.success(
                approvalService.addCaseComment(quoteId, approvalId, request), "Comment added");
    }

    @GetMapping("/approvals/pending")
    @Operation(summary = "Get all pending approvals")
    public ApiResponse<List<ApprovalRequestDto>> getPendingApprovals() {
        List<ApprovalRequestDto> pending = approvalService.getPendingApprovals();
        return ApiResponse.success(pending);
    }

    @PostMapping("/approvals/{approvalId}/approve")
    @Operation(summary = "Approve an approval request")
    public ApiResponse<ApprovalRequestDto> approve(
            @PathVariable Long approvalId,
            @RequestBody ApprovalResponseRequest request) {
        ApprovalRequestDto approval = approvalService.approveRequest(approvalId, request);
        return ApiResponse.success(approval, "Approval granted");
    }

    @PostMapping("/approvals/{approvalId}/reject")
    @Operation(summary = "Reject an approval request")
    public ApiResponse<ApprovalRequestDto> reject(
            @PathVariable Long approvalId,
            @RequestBody ApprovalResponseRequest request) {
        ApprovalRequestDto approval = approvalService.rejectRequest(approvalId, request);
        return ApiResponse.success(approval, "Approval rejected");
    }

    @PostMapping("/approvals/{approvalId}/escalate")
    @Operation(summary = "Escalate a pending approval after its SLA window")
    public ApiResponse<ApprovalRequestDto> escalate(@PathVariable Long approvalId) {
        ApprovalRequestDto approval = approvalService.escalateRequest(approvalId);
        return ApiResponse.success(approval, "Approval escalated");
    }
}
