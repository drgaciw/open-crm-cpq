package com.opencrm.crm.activity;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/activities") @RequiredArgsConstructor
@Tag(name = "Activities", description = "Manually recorded CRM timeline; references are never fetched")
public class ActivityController {
    private final ActivityService service;

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a note, task, email reference or meeting reference")
    public ApiResponse<ActivityDto> create(@Valid @RequestBody ActivityCreateRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping
    @Operation(summary = "Read a subject timeline, newest first; multiple subject filters intersect")
    public ApiResponse<List<ActivityDto>> timeline(@RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) Long contactId, @RequestParam(required = false) Long dealId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = service.timeline(organizationId, contactId, dealId, page, size);
        return ApiResponse.success(result.getContent(), ApiResponse.PageInfo.builder()
                .page(result.getNumber()).size(result.getSize()).totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages()).hasNext(result.hasNext()).hasPrevious(result.hasPrevious()).build());
    }

    @GetMapping("/{id}") @Operation(summary = "Read a manually recorded activity")
    public ApiResponse<ActivityDto> getById(@PathVariable Long id) { return ApiResponse.success(service.getById(id)); }

    @PostMapping("/{id}/complete") @Operation(summary = "Complete a task idempotently")
    public ApiResponse<ActivityDto> complete(@PathVariable Long id) { return ApiResponse.success(service.complete(id)); }
}
