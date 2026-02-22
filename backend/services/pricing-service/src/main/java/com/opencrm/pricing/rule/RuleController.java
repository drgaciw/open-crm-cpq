package com.opencrm.pricing.rule;

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
 * REST controller for Rule management.
 */
@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
@Tag(name = "Rules", description = "Rule management endpoints")
public class RuleController {

    private final RuleService ruleService;

    /**
     * Create a new rule.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new rule", description = "Creates a new rule in the system")
    public ApiResponse<RuleDto> create(@Valid @RequestBody RuleCreateRequest request) {
        RuleDto rule = ruleService.create(request);
        return ApiResponse.success(rule, "Rule created successfully");
    }

    /**
     * List rules by rule set ID.
     */
    @GetMapping("/by-rule-set/{ruleSetId}")
    @Operation(summary = "List rules by rule set", description = "Get a paginated list of rules for a specific rule set")
    public ApiResponse<Page<RuleDto>> listByRuleSet(
            @PathVariable Long ruleSetId,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<RuleDto> rules = ruleService.listByRuleSet(ruleSetId, pageable);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(rules.getNumber())
                .size(rules.getSize())
                .totalElements(rules.getTotalElements())
                .totalPages(rules.getTotalPages())
                .hasNext(rules.hasNext())
                .hasPrevious(rules.hasPrevious())
                .build();

        return ApiResponse.success(rules, pageInfo);
    }

    /**
     * Get a rule by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get rule by ID", description = "Retrieve a single rule by its ID")
    public ApiResponse<RuleDto> getById(@PathVariable Long id) {
        RuleDto rule = ruleService.getById(id);
        return ApiResponse.success(rule);
    }

    /**
     * Update a rule.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update rule", description = "Update an existing rule")
    public ApiResponse<RuleDto> update(
            @PathVariable Long id,
            @Valid @RequestBody RuleUpdateRequest request) {
        RuleDto rule = ruleService.update(id, request);
        return ApiResponse.success(rule, "Rule updated successfully");
    }

    /**
     * Delete a rule (soft delete).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete rule", description = "Soft delete a rule")
    public void delete(@PathVariable Long id) {
        ruleService.delete(id);
    }

    /**
     * Validate rule DRL syntax.
     */
    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate rule", description = "Validate DRL syntax without executing")
    public ApiResponse<Void> validate(@PathVariable Long id) {
        ruleService.validate(id);
        return ApiResponse.success(null, "Rule validated successfully");
    }

    /**
     * Analyze potential impact of a rule.
     */
    @GetMapping("/{id}/impact")
    @Operation(summary = "Rule impact analysis", description = "Analyze potential impact and usage signals for a rule")
    public ApiResponse<RuleImpactDto> impact(@PathVariable Long id) {
        RuleImpactDto impact = ruleService.getImpact(id);
        return ApiResponse.success(impact);
    }
}
