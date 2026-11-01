package com.opencrm.crm.organization;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.crm.contact.ContactDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Organization operations.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
@Tag(name = "Organizations", description = "Organization management endpoints")
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new organization", description = "Creates a new organization with the provided information")
    public ApiResponse<OrganizationDto> create(@Valid @RequestBody OrganizationCreateRequest request) {
        OrganizationDto organization = organizationService.create(request);
        return ApiResponse.success(organization, "Organization created successfully");
    }

    @GetMapping
    @Operation(summary = "List organizations", description = "Retrieves a paginated list of organizations with optional type filter")
    public ApiResponse<List<OrganizationDto>> list(
        @Parameter(description = "Filter by organization type")
        @RequestParam(required = false) OrganizationType type,
        @RequestParam(required = false) String industry,
        @RequestParam(required = false) String territory,
        @RequestParam(required = false) String ownerId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<OrganizationDto> organizations = industry == null && territory == null && ownerId == null
            ? organizationService.list(pageable, type)
            : organizationService.filter(null, type, industry, territory, ownerId, pageable);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
            .page(organizations.getNumber())
            .size(organizations.getSize())
            .totalElements(organizations.getTotalElements())
            .totalPages(organizations.getTotalPages())
            .hasNext(organizations.hasNext())
            .hasPrevious(organizations.hasPrevious())
            .build();

        return ApiResponse.success(organizations.getContent(), pageInfo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get organization by ID", description = "Retrieves a specific organization by its ID")
    public ApiResponse<OrganizationDto> getById(
        @Parameter(description = "Organization ID")
        @PathVariable Long id
    ) {
        OrganizationDto organization = organizationService.getById(organizationService.resolveAccountId(id));
        return ApiResponse.success(organization);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update organization", description = "Updates an existing organization with the provided information")
    public ApiResponse<OrganizationDto> update(
        @Parameter(description = "Organization ID")
        @PathVariable Long id,
        @Valid @RequestBody OrganizationUpdateRequest request
    ) {
        OrganizationDto organization = organizationService.update(organizationService.resolveAccountId(id), request);
        return ApiResponse.success(organization, "Organization updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete organization", description = "Soft deletes an organization by its ID")
    public ApiResponse<Void> delete(
        @Parameter(description = "Organization ID")
        @PathVariable Long id
    ) {
        organizationService.delete(organizationService.resolveAccountId(id));
        return ApiResponse.success(null, "Deleted successfully");
    }

    @GetMapping("/search")
    @Operation(summary = "Search organizations", description = "Searches organizations by name")
    public ApiResponse<List<OrganizationDto>> search(
        @Parameter(description = "Search query (searches organization name)")
        @RequestParam String q,
        @RequestParam(required = false) String industry,
        @RequestParam(required = false) String territory,
        @RequestParam(required = false) String ownerId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<OrganizationDto> organizations = industry == null && territory == null && ownerId == null
            ? organizationService.search(q, pageable)
            : organizationService.filter(q, null, industry, territory, ownerId, pageable);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
            .page(organizations.getNumber())
            .size(organizations.getSize())
            .totalElements(organizations.getTotalElements())
            .totalPages(organizations.getTotalPages())
            .hasNext(organizations.hasNext())
            .hasPrevious(organizations.hasPrevious())
            .build();

        return ApiResponse.success(organizations.getContent(), pageInfo);
    }

    @GetMapping("/{id}/contacts")
    @Operation(summary = "Get organization contacts", description = "Retrieves all contacts belonging to an organization")
    public ApiResponse<List<ContactDto>> getContacts(
        @Parameter(description = "Organization ID")
        @PathVariable Long id,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ContactDto> contacts = organizationService.getContacts(organizationService.resolveAccountId(id), pageable);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
            .page(contacts.getNumber())
            .size(contacts.getSize())
            .totalElements(contacts.getTotalElements())
            .totalPages(contacts.getTotalPages())
            .hasNext(contacts.hasNext())
            .hasPrevious(contacts.hasPrevious())
            .build();

        return ApiResponse.success(contacts.getContent(), pageInfo);
    }
}
