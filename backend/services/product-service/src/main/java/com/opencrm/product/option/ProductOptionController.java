package com.opencrm.product.option;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-options")
@RequiredArgsConstructor
@Tag(name = "Product Options", description = "Product option group and option management")
public class ProductOptionController {

    private final ProductOptionService optionService;

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an option group")
    public ApiResponse<ProductOptionGroupDto> createGroup(@Valid @RequestBody OptionGroupCreateRequest request) {
        ProductOptionGroupDto group = optionService.createGroup(request);
        return ApiResponse.success(group, "Option group created successfully");
    }

    @GetMapping("/groups/{id}")
    @Operation(summary = "Get option group by ID")
    public ApiResponse<ProductOptionGroupDto> getGroup(@PathVariable Long id) {
        ProductOptionGroupDto group = optionService.getGroupById(id);
        return ApiResponse.success(group);
    }

    @GetMapping("/groups/product/{productId}")
    @Operation(summary = "Get option groups by product ID")
    public ApiResponse<List<ProductOptionGroupDto>> getGroupsByProduct(@PathVariable Long productId) {
        List<ProductOptionGroupDto> groups = optionService.getGroupsByProductId(productId);
        return ApiResponse.success(groups);
    }

    @PostMapping("/groups/{groupId}/options")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add option to a group")
    public ApiResponse<ProductOptionGroupDto> addOption(
            @PathVariable Long groupId,
            @Valid @RequestBody OptionCreateRequest request) {
        ProductOptionGroupDto group = optionService.addOption(groupId, request);
        return ApiResponse.success(group, "Option added successfully");
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an option group")
    public void deleteGroup(@PathVariable Long id) {
        optionService.deleteGroup(id);
    }
}
