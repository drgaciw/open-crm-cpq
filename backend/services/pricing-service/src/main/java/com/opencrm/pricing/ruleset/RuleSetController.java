package com.opencrm.pricing.ruleset;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for RuleSet management.
 */
@RestController
@RequestMapping("/api/v1/rule-sets")
@RequiredArgsConstructor
@Tag(name = "Rule Sets", description = "Rule set management endpoints")
public class RuleSetController {

    private final RuleSetService ruleSetService;

    /**
     * Create a new rule set.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new rule set", description = "Creates a new rule set in the system")
    public ApiResponse<RuleSetDto> create(@Valid @RequestBody RuleSetCreateRequest request) {
        RuleSetDto ruleSet = ruleSetService.create(request);
        return ApiResponse.success(ruleSet, "Rule set created successfully");
    }

    /**
     * List all rule sets with optional filters.
     */
    @GetMapping
    @Operation(summary = "List rule sets", description = "Get a paginated list of rule sets with optional filtering")
    public ApiResponse<Page<RuleSetDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) RuleSetStatus status) {
        Page<RuleSetDto> ruleSets = ruleSetService.list(pageable, status);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(ruleSets.getNumber())
                .size(ruleSets.getSize())
                .totalElements(ruleSets.getTotalElements())
                .totalPages(ruleSets.getTotalPages())
                .hasNext(ruleSets.hasNext())
                .hasPrevious(ruleSets.hasPrevious())
                .build();

        return ApiResponse.success(ruleSets, pageInfo);
    }

    /**
     * Get a rule set by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get rule set by ID", description = "Retrieve a single rule set by its ID")
    public ApiResponse<RuleSetDto> getById(@PathVariable Long id) {
        RuleSetDto ruleSet = ruleSetService.getById(id);
        return ApiResponse.success(ruleSet);
    }

    /**
     * Update a rule set.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update rule set", description = "Update an existing rule set")
    public ApiResponse<RuleSetDto> update(
            @PathVariable Long id,
            @Valid @RequestBody RuleSetUpdateRequest request) {
        RuleSetDto ruleSet = ruleSetService.update(id, request);
        return ApiResponse.success(ruleSet, "Rule set updated successfully");
    }

    /**
     * Delete a rule set (soft delete).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete rule set", description = "Soft delete a rule set")
    public void delete(@PathVariable Long id) {
        ruleSetService.delete(id);
    }

    /**
     * Activate a rule set.
     */
    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate rule set", description = "Activate a rule set (DRAFT -> ACTIVE)")
    public ApiResponse<RuleSetDto> activate(@PathVariable Long id) {
        RuleSetDto ruleSet = ruleSetService.activate(id);
        return ApiResponse.success(ruleSet, "Rule set activated successfully");
    }

    /**
     * Deprecate a rule set.
     */
    @PostMapping("/{id}/deprecate")
    @Operation(summary = "Deprecate rule set", description = "Deprecate a rule set (ACTIVE -> DEPRECATED)")
    public ApiResponse<RuleSetDto> deprecate(@PathVariable Long id) {
        RuleSetDto ruleSet = ruleSetService.deprecate(id);
        return ApiResponse.success(ruleSet, "Rule set deprecated successfully");
    }
}
