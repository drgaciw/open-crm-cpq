package com.opencrm.approval.controller;

import com.opencrm.approval.dto.ApprovalMatrixDto;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.DefineApprovalMatrixRequest;
import com.opencrm.approval.dto.SubmitForApprovalRequest;
import com.opencrm.approval.service.ApprovalMatrixService;
import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Approval matrix and the quote-BFF submission path (BC-04-03). Writes of approval requests still originate at
 * quote-service, which calls these routes with the caller's token (ADR-013). The Angular inbox does not call here.
 */
@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
@Tag(name = "Approval Matrix", description = "Versioned breach-to-role matrix, submit, and approver inbox")
public class ApprovalMatrixController {

    private final ApprovalMatrixService approvalMatrixService;

    @PostMapping("/matrices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "DefineApprovalMatrix: a new version mapping breach types and amounts to approver roles")
    public ApiResponse<ApprovalMatrixDto> define(@Valid @RequestBody DefineApprovalMatrixRequest request) {
        return ApiResponse.success(approvalMatrixService.define(request), "Approval matrix defined");
    }

    @GetMapping("/matrices/{id}")
    @Operation(summary = "Get one approval-matrix version")
    public ApiResponse<ApprovalMatrixDto> getById(@PathVariable Long id) {
        return ApiResponse.success(approvalMatrixService.getById(id));
    }

    @PostMapping("/matrix-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "SubmitForApproval: create approval requests from the active matrix and a policy verdict")
    public ApiResponse<List<ApprovalRequestDto>> submit(@Valid @RequestBody SubmitForApprovalRequest request) {
        return ApiResponse.success(approvalMatrixService.submitForApproval(request), "Submitted for approval");
    }

    @GetMapping("/inbox")
    @Operation(summary = "ListPendingApprovals for the caller: pending rows their role may decide, in their tenant")
    public ApiResponse<List<ApprovalRequestDto>> inbox(
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String tenantId) {
        return ApiResponse.success(approvalMatrixService.listPendingApprovals(tier, tenantId));
    }
}
