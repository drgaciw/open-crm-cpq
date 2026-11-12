package com.opencrm.approval.controller;

import com.opencrm.approval.dto.CommercialPolicyCreateRequest;
import com.opencrm.approval.dto.CommercialPolicyDto;
import com.opencrm.approval.service.CommercialPolicyService;
import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for commercial policy definition (BC-04-01, DRG-441).
 * API boundary exposes {@link CommercialPolicyDto} only — never the JPA entity (ADR-006).
 */
@RestController
@RequestMapping("/api/v1/commercial-policies")
@RequiredArgsConstructor
@Tag(name = "Commercial Policies", description = "Declarative, versioned commercial policy definition")
public class CommercialPolicyController {

    private final CommercialPolicyService commercialPolicyService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "DefinePolicy: define a new commercial policy version with its thresholds")
    public ApiResponse<CommercialPolicyDto> define(@Valid @RequestBody CommercialPolicyCreateRequest request) {
        CommercialPolicyDto defined = commercialPolicyService.define(request);
        return ApiResponse.success(defined, "Commercial policy defined");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a commercial policy version by ID")
    public ApiResponse<CommercialPolicyDto> getById(@PathVariable Long id) {
        return ApiResponse.success(commercialPolicyService.getById(id));
    }

    @GetMapping
    @Operation(summary = "List commercial policy versions, newest first by ID")
    public ApiResponse<Page<CommercialPolicyDto>> list(Pageable pageable) {
        return ApiResponse.success(commercialPolicyService.list(pageable));
    }

    @GetMapping("/applicable")
    @Operation(summary = "List ACTIVE policies applicable to a segment/productFamily/region scope")
    public ApiResponse<List<CommercialPolicyDto>> findApplicable(
            @RequestParam(required = false) String segment,
            @RequestParam(required = false) String productFamily,
            @RequestParam(required = false) String region) {
        return ApiResponse.success(commercialPolicyService.findApplicable(segment, productFamily, region));
    }

    @PostMapping("/{id}/deprecate")
    @Operation(summary = "Retire a policy version directly, without a replacement")
    public ApiResponse<CommercialPolicyDto> deprecate(@PathVariable Long id) {
        CommercialPolicyDto deprecated = commercialPolicyService.deprecate(id);
        return ApiResponse.success(deprecated, "Commercial policy deprecated");
    }
}
