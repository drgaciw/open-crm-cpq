package com.opencrm.quote.approval;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.quote.client.RoutingApprovalDto;
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
 * Quote BFF for the approval matrix (ADR-013). The browser calls these routes; approval-routing is reached only
 * from {@link ApprovalMatrixBff}.
 */
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Tag(name = "Approval Matrix", description = "Submit a quote on the approval matrix and list the caller's inbox")
public class ApprovalMatrixController {

    private final ApprovalMatrixBff approvalMatrixBff;

    @PostMapping("/{quoteId}/approvals/matrix")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "SubmitForApproval through the quote BFF")
    public ApiResponse<List<RoutingApprovalDto>> submit(
            @PathVariable Long quoteId,
            @Valid @RequestBody MatrixApprovalSubmitRequest request) {
        return ApiResponse.success(approvalMatrixBff.submitForApproval(quoteId, request), "Submitted for approval");
    }

    @GetMapping("/approvals/inbox")
    @Operation(summary = "ListPendingApprovals for the caller")
    public ApiResponse<List<RoutingApprovalDto>> inbox(@RequestParam(required = false) String tier) {
        return ApiResponse.success(approvalMatrixBff.listPendingApprovals(tier));
    }
}
