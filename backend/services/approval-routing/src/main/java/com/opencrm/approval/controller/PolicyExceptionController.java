package com.opencrm.approval.controller;

import com.opencrm.approval.dto.PolicyExceptionDto;
import com.opencrm.approval.dto.RecordPolicyExceptionRequest;
import com.opencrm.approval.service.PolicyExceptionService;
import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canonical {@code RecordException} writer. The quote BFF calls this on the approval-routing URL.
 * It is not a gateway route, so the seller path stays {@code /api/v1/quotes/{id}/policy-exceptions}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Policy Exceptions", description = "Time-boxed exceptions for one quote version and policy")
public class PolicyExceptionController {

    private final PolicyExceptionService policyExceptionService;

    @PostMapping("/api/v1/policy-exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "RecordException: justify a time-boxed exception for one quote version and policy")
    public ApiResponse<PolicyExceptionDto> record(@Valid @RequestBody RecordPolicyExceptionRequest request) {
        return ApiResponse.success(policyExceptionService.record(request), "Policy exception recorded");
    }
}
