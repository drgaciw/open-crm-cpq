package com.opencrm.crm.merge;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merge command for organizations. The path stays under {@code /api/v1/organizations/**}
 * so the existing gateway route reaches crm-service. The survivor id is a request field
 * the caller sets; it is not inferred.
 */
@RestController
@RequestMapping("/api/v1/organizations/merge")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Account merge")
public class AccountMergeController {

    private final AccountMergeService accountMergeService;

    @PostMapping
    @Operation(summary = "Merge two organizations",
            description = "Keeps survivorId, soft-deletes mergedAwayId, repoints CRM contacts, leads, "
                    + "and deals, and emits one AccountsMerged event. Other services repoint their own rows "
                    + "by consuming that event.")
    public ApiResponse<AccountMergeResult> merge(@Valid @RequestBody AccountMergeRequest request) {
        AccountMergeResult result = accountMergeService.merge(request);
        return ApiResponse.success(result, "Accounts merged");
    }
}
