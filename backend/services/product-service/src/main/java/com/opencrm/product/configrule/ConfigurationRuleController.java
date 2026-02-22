package com.opencrm.product.configrule;

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

import java.util.List;

@RestController
@RequestMapping("/api/v1/configuration")
@RequiredArgsConstructor
@Tag(name = "Configuration Rules", description = "Product configuration rule management")
public class ConfigurationRuleController {

    private final ConfigurationRuleService configRuleService;

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a configuration rule")
    public ApiResponse<ConfigurationRuleDto> create(@Valid @RequestBody ConfigRuleCreateRequest request) {
        ConfigurationRuleDto rule = configRuleService.create(request);
        return ApiResponse.success(rule, "Configuration rule created successfully");
    }

    @GetMapping("/rules")
    @Operation(summary = "List all configuration rules")
    public ApiResponse<Page<ConfigurationRuleDto>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<ConfigurationRuleDto> rules = configRuleService.list(pageable);
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

    @GetMapping("/rules/{id}")
    @Operation(summary = "Get configuration rule by ID")
    public ApiResponse<ConfigurationRuleDto> getById(@PathVariable Long id) {
        ConfigurationRuleDto rule = configRuleService.getById(id);
        return ApiResponse.success(rule);
    }

    @GetMapping("/rules/product/{productId}")
    @Operation(summary = "Get configuration rules by source product")
    public ApiResponse<List<ConfigurationRuleDto>> getByProduct(@PathVariable Long productId) {
        List<ConfigurationRuleDto> rules = configRuleService.getBySourceProduct(productId);
        return ApiResponse.success(rules);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate a product configuration")
    public ApiResponse<List<String>> validate(@RequestBody List<Long> selectedProductIds) {
        List<String> errors = configRuleService.validateConfiguration(selectedProductIds);
        if (errors.isEmpty()) {
            return ApiResponse.success(errors, "Configuration is valid");
        }
        return ApiResponse.success(errors, "Configuration has validation errors");
    }

    @DeleteMapping("/rules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a configuration rule")
    public void delete(@PathVariable Long id) {
        configRuleService.delete(id);
    }
}
