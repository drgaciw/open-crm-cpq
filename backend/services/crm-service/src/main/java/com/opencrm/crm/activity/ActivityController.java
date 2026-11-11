package com.opencrm.crm.activity;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Notes and tasks on an organization, contact, or deal timeline (DRG-520 / ADR-030).
 * Email and meeting creation is rejected until mailbox ingestion (DRG-521).
 */
@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
@Tag(name = "Activities", description = "Notes and tasks on a CRM timeline")
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Log a note or task", description = "Creates a NOTE or TASK linked to at least one of organization, contact, or deal. EMAIL and MEETING are reserved for mailbox ingestion.")
    public ApiResponse<ActivityDto> create(@Valid @RequestBody ActivityCreateRequest request) {
        return ApiResponse.success(activityService.create(request), "Activity created successfully");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an activity", description = "Returns one note or task that has not been deleted")
    public ApiResponse<ActivityDto> getById(
            @Parameter(description = "Activity id") @PathVariable Long id) {
        return ApiResponse.success(activityService.getById(id));
    }

    @GetMapping
    @Operation(summary = "Read a timeline", description = "Returns activities for exactly one of organizationId, contactId, or dealId, newest first")
    public ApiResponse<List<ActivityDto>> timeline(
            @Parameter(description = "Organization id") @RequestParam(required = false) Long organizationId,
            @Parameter(description = "Contact id") @RequestParam(required = false) Long contactId,
            @Parameter(description = "Deal id") @RequestParam(required = false) Long dealId,
            @Parameter(description = "NOTE, TASK, EMAIL, or MEETING") @RequestParam(required = false) ActivityType type,
            @PageableDefault(size = 20, sort = {"occurredAt", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ActivityDto> page = activityService.timeline(organizationId, contactId, dealId, type, pageable);
        return ApiResponse.success(page.getContent(), pageInfo(page));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete a task", description = "Sets completedAt once. A second call keeps the original timestamp. Notes are rejected.")
    public ApiResponse<ActivityDto> complete(
            @Parameter(description = "Activity id") @PathVariable Long id) {
        return ApiResponse.success(activityService.complete(id), "Task completed successfully");
    }

    private static ApiResponse.PageInfo pageInfo(Page<?> page) {
        return ApiResponse.PageInfo.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }
}
