package com.opencrm.crm.duplicate;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * RED stub. Endpoints exist so the contract tests compile; they do not return suggestions yet.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Duplicate candidates", description = "not implemented")
public class DuplicateCandidateController {

    private final DuplicateCandidateService duplicateCandidateService;

    @GetMapping("/api/v1/organizations/{id}/duplicate-candidates")
    @Operation(summary = "Suggest duplicate organizations", description = "not implemented")
    public ApiResponse<List<DuplicateCandidateDto>> organizationCandidates(@PathVariable Long id) {
        if (duplicateCandidateService == null) {
            throw new IllegalStateException("missing duplicate candidate service");
        }
        return ApiResponse.success(List.of());
    }

    @GetMapping("/api/v1/contacts/{id}/duplicate-candidates")
    @Operation(summary = "Suggest duplicate contacts", description = "not implemented")
    public ApiResponse<List<DuplicateCandidateDto>> contactCandidates(@PathVariable Long id) {
        if (duplicateCandidateService == null) {
            throw new IllegalStateException("missing duplicate candidate service");
        }
        return ApiResponse.success(List.of());
    }
}
