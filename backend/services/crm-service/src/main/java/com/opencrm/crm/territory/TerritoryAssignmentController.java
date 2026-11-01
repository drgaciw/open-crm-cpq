package com.opencrm.crm.territory;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Territory and segment assignment for an organization (the account).
 * GetSegment is the read pricing, product, and contract callers will use later.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Territory and segment", description = "Assign territory and segment, and read the segment")
public class TerritoryAssignmentController {

    private final TerritoryAssignmentService territoryAssignmentService;

    @PostMapping("/territory-rules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an assignment rule",
            description = "Stores a rule keyed by country, optional state, industry, and employee-count bounds")
    public ApiResponse<TerritoryRuleResponse> createRule(@Valid @RequestBody TerritoryRuleRequest request) {
        return ApiResponse.success(territoryAssignmentService.createRule(request), "Assignment rule created");
    }

    @GetMapping("/territory-rules")
    @Operation(summary = "List assignment rules", description = "Returns active and inactive rules that are not deleted")
    public ApiResponse<List<TerritoryRuleResponse>> listRules() {
        return ApiResponse.success(territoryAssignmentService.listRules());
    }

    @PostMapping("/{id}/territory-assignment")
    @Operation(summary = "AssignTerritory and AssignSegment",
            description = "Applies geography, industry, and size rules. A match stores the territory and segment "
                    + "and emits one TerritoryAssigned event when the assignment is made or changed. "
                    + "No match leaves the organization unassigned and emits nothing.")
    public ApiResponse<OrganizationSegmentResponse> assign(
            @Parameter(description = "Organization ID") @PathVariable Long id) {
        OrganizationSegmentResponse result = territoryAssignmentService.assign(id);
        String message = result.isAssigned()
                ? "Territory and segment assigned"
                : "Organization left unassigned";
        return ApiResponse.success(result, message);
    }

    @GetMapping("/{id}/segment")
    @Operation(summary = "GetSegment",
            description = "Reads the segment assigned to the organization. "
                    + "An unassigned organization returns assigned=false and a null segment.")
    public ApiResponse<OrganizationSegmentResponse> getSegment(
            @Parameter(description = "Organization ID") @PathVariable Long id) {
        return ApiResponse.success(territoryAssignmentService.getSegment(id));
    }
}
