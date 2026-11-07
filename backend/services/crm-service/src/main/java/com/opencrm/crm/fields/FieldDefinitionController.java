package com.opencrm.crm.fields;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin field registry. Routes stay under {@code /api/v1/field-definitions/**}.
 * Gateway, saved views, and record DTO maps are a later slice.
 */
@RestController
@Validated
@RequestMapping("/api/v1/field-definitions")
@RequiredArgsConstructor
@Tag(name = "Field definitions", description = "Typed custom field registry for fixture tenants")
public class FieldDefinitionController {

    private final FieldRegistryService fieldRegistryService;

    @GetMapping
    @Operation(summary = "List field definitions for one fixture tenant and entity")
    public ApiResponse<List<FieldDefinitionResponse>> list(
            @RequestParam String tenant,
            @RequestParam FieldEntityKind entity) {
        return ApiResponse.success(fieldRegistryService.list(tenant, entity));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a custom field definition")
    public ApiResponse<FieldDefinitionResponse> create(
            @RequestParam String tenant,
            @Valid @RequestBody FieldDefinitionCreateRequest request) {
        return ApiResponse.success(fieldRegistryService.create(tenant, request), "Field definition created");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one field definition in a fixture tenant")
    public ApiResponse<FieldDefinitionResponse> get(
            @RequestParam String tenant,
            @PathVariable long id) {
        return ApiResponse.success(fieldRegistryService.get(tenant, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Relabel, retype within a storage class, hide, or reorder a field")
    public ApiResponse<FieldDefinitionResponse> update(
            @RequestParam String tenant,
            @PathVariable long id,
            @Valid @RequestBody FieldDefinitionUpdateRequest request) {
        return ApiResponse.success(fieldRegistryService.update(tenant, id, request), "Field definition updated");
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Archive a custom field. The row and its values stay")
    public ApiResponse<FieldDefinitionResponse> archive(
            @RequestParam String tenant,
            @PathVariable long id,
            @Valid @RequestBody FieldArchiveRequest request) {
        return ApiResponse.success(fieldRegistryService.archive(tenant, id, request), "Field definition archived");
    }

    @PostMapping("/{id}/options")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a select option row")
    public ApiResponse<FieldDefinitionResponse> addOption(
            @RequestParam String tenant,
            @PathVariable long id,
            @Valid @RequestBody FieldOptionWriteRequest request) {
        return ApiResponse.success(fieldRegistryService.addOption(tenant, id, request), "Field option created");
    }

    @PutMapping("/{id}/options/{optionId}")
    @Operation(summary = "Rename a select option without changing its id")
    public ApiResponse<FieldDefinitionResponse> renameOption(
            @RequestParam String tenant,
            @PathVariable long id,
            @PathVariable long optionId,
            @Valid @RequestBody FieldOptionWriteRequest request) {
        return ApiResponse.success(
                fieldRegistryService.renameOption(tenant, id, optionId, request),
                "Field option renamed");
    }

    @PostMapping("/{id}/options/{optionId}/archive")
    @Operation(summary = "Archive a select option. Values that point at it stay")
    public ApiResponse<FieldDefinitionResponse> archiveOption(
            @RequestParam String tenant,
            @PathVariable long id,
            @PathVariable long optionId,
            @Valid @RequestBody FieldArchiveRequest request) {
        return ApiResponse.success(
                fieldRegistryService.archiveOption(tenant, id, optionId, request),
                "Field option archived");
    }

    @PutMapping("/{id}/values")
    @Operation(summary = "Write a typed value. Service actors are rejected")
    public ApiResponse<FieldValueResponse> upsertValue(
            @RequestParam String tenant,
            @PathVariable long id,
            @Valid @RequestBody FieldValueWriteRequest request) {
        return ApiResponse.success(fieldRegistryService.upsertValue(tenant, id, request), "Field value saved");
    }

    @GetMapping("/{id}/values")
    @Operation(summary = "Read the typed value for one record")
    public ApiResponse<FieldValueResponse> getValue(
            @RequestParam String tenant,
            @PathVariable long id,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long contactId,
            @RequestParam(required = false) Long dealId) {
        return ApiResponse.success(fieldRegistryService.getValue(tenant, id, organizationId, contactId, dealId));
    }

    @DeleteMapping("/{id}/values")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear a typed value. Service actors are rejected")
    public void clearValue(
            @RequestParam String tenant,
            @PathVariable long id,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long contactId,
            @RequestParam(required = false) Long dealId) {
        fieldRegistryService.clearValue(tenant, id, organizationId, contactId, dealId);
    }
}
