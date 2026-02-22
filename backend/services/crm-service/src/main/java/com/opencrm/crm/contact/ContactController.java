package com.opencrm.crm.contact;

import com.opencrm.common.dto.ApiResponse;
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
 * REST controller for Contact operations.
 */
@RestController
@RequestMapping("/api/v1/contacts")
@RequiredArgsConstructor
@Tag(name = "Contacts", description = "Contact management endpoints")
public class ContactController {

    private final ContactService contactService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new contact", description = "Creates a new contact with the provided information")
    public ApiResponse<ContactDto> create(@Valid @RequestBody ContactCreateRequest request) {
        ContactDto contact = contactService.create(request);
        return ApiResponse.success(contact, "Contact created successfully");
    }

    @GetMapping
    @Operation(summary = "List contacts", description = "Retrieves a paginated list of contacts with optional status filter")
    public ApiResponse<List<ContactDto>> list(
        @Parameter(description = "Filter by contact status")
        @RequestParam(required = false) ContactStatus status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ContactDto> contacts = contactService.list(pageable, status);

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

    @GetMapping("/{id}")
    @Operation(summary = "Get contact by ID", description = "Retrieves a specific contact by its ID")
    public ApiResponse<ContactDto> getById(
        @Parameter(description = "Contact ID")
        @PathVariable Long id
    ) {
        ContactDto contact = contactService.getById(id);
        return ApiResponse.success(contact);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update contact", description = "Updates an existing contact with the provided information")
    public ApiResponse<ContactDto> update(
        @Parameter(description = "Contact ID")
        @PathVariable Long id,
        @Valid @RequestBody ContactUpdateRequest request
    ) {
        ContactDto contact = contactService.update(id, request);
        return ApiResponse.success(contact, "Contact updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete contact", description = "Soft deletes a contact by its ID")
    public ApiResponse<Void> delete(
        @Parameter(description = "Contact ID")
        @PathVariable Long id
    ) {
        contactService.delete(id);
        return ApiResponse.success(null, "Deleted successfully");
    }

    @GetMapping("/search")
    @Operation(summary = "Search contacts", description = "Searches contacts by first or last name")
    public ApiResponse<List<ContactDto>> search(
        @Parameter(description = "Search query (searches first and last name)")
        @RequestParam String q,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<ContactDto> contacts = contactService.search(q, pageable);

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
