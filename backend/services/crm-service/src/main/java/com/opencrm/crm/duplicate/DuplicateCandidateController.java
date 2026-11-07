package com.opencrm.crm.duplicate;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only duplicate suggestions. Merge stays {@code POST /api/v1/organizations/merge} (BC-12-04).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Duplicate candidates", description = DuplicateCandidateController.SUGGESTION_DESCRIPTION)
public class DuplicateCandidateController {

    static final String SUGGESTION_DESCRIPTION =
            "Read-only duplicate suggestions. A suggestion is not a merge. "
                    + "Merge is POST /api/v1/organizations/merge (BC-12-04), called explicitly by a user. "
                    + "Free-mail and consumer domains never imply an organization, never produce an organization candidate, "
                    + "and never count as a same-company signal for contacts.";

    private final DuplicateCandidateService duplicateCandidateService;
    private final DuplicateCandidateAccess access;

    @GetMapping("/api/v1/organizations/{id}/duplicate-candidates")
    @Operation(summary = "Suggest duplicate organizations", description = SUGGESTION_DESCRIPTION)
    public ApiResponse<List<DuplicateCandidateDto>> organizationCandidates(
            @Parameter(description = "Organization row id") @PathVariable Long id) {
        access.requireReviewAccess();
        return ApiResponse.success(duplicateCandidateService.organizationCandidates(id));
    }

    @GetMapping("/api/v1/contacts/{id}/duplicate-candidates")
    @Operation(summary = "Suggest duplicate contacts", description = SUGGESTION_DESCRIPTION)
    public ApiResponse<List<DuplicateCandidateDto>> contactCandidates(
            @Parameter(description = "Contact row id") @PathVariable Long id) {
        access.requireReviewAccess();
        return ApiResponse.success(duplicateCandidateService.contactCandidates(id));
    }
}
