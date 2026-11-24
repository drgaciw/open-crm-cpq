package com.opencrm.quote.approval;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Seller write path for a policy exception. approval-routing is the only store (ADR-013).
 */
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Tag(name = "Policy Exceptions", description = "Record a time-boxed exception on a quote version")
public class QuotePolicyExceptionController {

    private final QuotePolicyExceptionService quotePolicyExceptionService;

    @PostMapping("/{quoteId}/policy-exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "RecordException for one quote version and policy")
    public ApiResponse<PolicyExceptionResponse> record(
            @PathVariable Long quoteId,
            @Valid @RequestBody RecordQuotePolicyExceptionRequest request) {
        return ApiResponse.success(
                quotePolicyExceptionService.record(quoteId, request), "Policy exception recorded");
    }
}
