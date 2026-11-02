package com.opencrm.crm.buyingrole;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Buying committee for a deal. Paths sit under {@code /api/v1/deals/**}, which the gateway
 * already routes to crm-service.
 */
@RestController
@RequestMapping("/api/v1/deals/{dealId}/buying-roles")
@RequiredArgsConstructor
@Tag(name = "Deal buying roles", description = "Contacts attached to a deal with a closed buying role")
public class DealBuyingRoleController {

    private final DealBuyingRoleService dealBuyingRoleService;

    @GetMapping
    @Operation(summary = "List buying roles", description = "Active buying-committee seats on the deal")
    public ApiResponse<List<DealBuyingRoleDto>> list(@PathVariable Long dealId) {
        return ApiResponse.success(dealBuyingRoleService.list(dealId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign a buying role",
            description = "Attaches a contact to the deal with one closed role. A second role for the same contact is rejected.")
    public ApiResponse<DealBuyingRoleDto> assign(@PathVariable Long dealId,
                                                 @Valid @RequestBody AssignBuyingRoleRequest request) {
        return ApiResponse.success(dealBuyingRoleService.assign(dealId, request), "Buying role assigned");
    }

    @DeleteMapping("/{contactId}")
    @Operation(summary = "Remove a buying role", description = "Soft-deletes the contact's role on the deal")
    public ApiResponse<Void> remove(@PathVariable Long dealId, @PathVariable Long contactId) {
        dealBuyingRoleService.remove(dealId, contactId);
        return ApiResponse.success(null, "Buying role removed");
    }
}
