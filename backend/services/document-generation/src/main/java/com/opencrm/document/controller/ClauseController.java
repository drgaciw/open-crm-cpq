package com.opencrm.document.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.document.dto.*;
import com.opencrm.document.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/documents/clauses")
@Tag(name = "Clause Library", description = "Tenant-scoped versioned clauses and deterministic selection")
public class ClauseController {
    private final ClauseLibraryService library;
    private final ClauseSelectionService selection;
    @PostMapping @Operation(summary = "AddClause: create a DRAFT first revision")
    public ApiResponse<ClauseDto> add(@Valid @RequestBody AddClause request) { return ApiResponse.success(library.addClause(request)); }
    @PostMapping("/{clauseId}/versions") @Operation(summary = "Create a new immutable DRAFT revision")
    public ApiResponse<ClauseDto> revise(@PathVariable Long clauseId, @Valid @RequestBody ClauseRevisionRequest request) {
        return ApiResponse.success(library.revise(clauseId, request));
    }
    @PostMapping("/versions/{id}/approve") @Operation(summary = "Approve a DRAFT revision as the authenticated actor")
    public ApiResponse<ClauseDto> approve(@PathVariable Long id) { return ApiResponse.success(library.approve(id)); }
    @PostMapping("/versions/{id}/retire") @Operation(summary = "Retire a revision from future selections")
    public ApiResponse<ClauseDto> retire(@PathVariable Long id) { return ApiResponse.success(library.retire(id)); }
    @GetMapping @Operation(summary = "List all clause revisions in the authenticated tenant")
    public ApiResponse<List<ClauseDto>> list() { return ApiResponse.success(library.list()); }
    @GetMapping("/versions/{id}") @Operation(summary = "Fetch an exact clause revision")
    public ApiResponse<ClauseDto> get(@PathVariable Long id) { return ApiResponse.success(library.get(id)); }
    @PostMapping("/select") @Operation(summary = "Preview matching latest APPROVED revisions; no expressions are executed")
    public ApiResponse<List<SelectedClauseDto>> select(@Valid @RequestBody ClauseSelectionContext context) {
        return ApiResponse.success(selection.select(context));
    }
}
