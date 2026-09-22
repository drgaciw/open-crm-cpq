package com.opencrm.quote.approval.suggestion;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only advisory endpoint (ADR-015). Always 200: a missing suggestion is a status, not an error, so
 * the approval dialog can never be broken by it.
 */
@RestController
@RequestMapping("/api/v1/quotes")
@RequiredArgsConstructor
@Tag(name = "Approvals", description = "Quote approval workflow endpoints")
public class ApprovalTierSuggestionController {

    private final ApprovalTierSuggestionService suggestionService;

    @GetMapping("/{quoteId}/approvals/tier-suggestion")
    @Operation(summary = "Advisory approval tier suggestion for a quote (never a decision)")
    public ApiResponse<ApprovalTierSuggestionDto> tierSuggestion(@PathVariable Long quoteId) {
        return ApiResponse.success(suggestionService.suggest(quoteId));
    }
}
