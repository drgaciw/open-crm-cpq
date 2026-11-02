package com.opencrm.crm.playbook;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Playbook definitions and GetPlaybookStep (DRG-486 / BC-13-02).
 * <p>
 * {@code GET /api/v1/playbooks/{id}/steps/next?stage=PROSPECTING&completedStepIds=1,2}
 * returns the next incomplete step for that stage inside {@link ApiResponse}.
 * When the playbook exists and the stage has nothing left, the status is 200 and
 * {@code data.step} is null. A missing playbook is 404. {@code data.version} is a
 * local integer because BC-18 is not landed. Steps are not coupled to buying roles.
 */
@RestController
@RequestMapping("/api/v1/playbooks")
@RequiredArgsConstructor
@Tag(name = "Playbooks", description = "Versioned sales playbooks and next-step lookup")
public class PlaybookController {

    private final PlaybookService playbookService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a playbook", description = "Creates a playbook at definition version 1, optionally with its initial steps and questions")
    public ApiResponse<PlaybookDto> create(@Valid @RequestBody PlaybookCreateRequest request) {
        return ApiResponse.success(playbookService.create(request), "Playbook created successfully");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a playbook", description = "Returns the playbook, its local definition version, and steps ordered by stage then sort order")
    public ApiResponse<PlaybookDto> getById(
            @Parameter(description = "Playbook id") @PathVariable Long id) {
        return ApiResponse.success(playbookService.getById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Rename a playbook", description = "Updates the name only. The definition version stays the same")
    public ApiResponse<PlaybookDto> update(
            @Parameter(description = "Playbook id") @PathVariable Long id,
            @Valid @RequestBody PlaybookUpdateRequest request) {
        return ApiResponse.success(playbookService.update(id, request), "Playbook updated successfully");
    }

    @PostMapping("/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a playbook step", description = "Appends or inserts a step and its questions, then increments the local definition version")
    public ApiResponse<PlaybookDto> addStep(
            @Parameter(description = "Playbook id") @PathVariable Long id,
            @Valid @RequestBody PlaybookStepWriteRequest request) {
        return ApiResponse.success(playbookService.addStep(id, request), "Playbook step added successfully");
    }

    @PutMapping("/{id}/steps/{stepId}")
    @Operation(summary = "Update a playbook step", description = "Replaces stage, order, or questions. The local definition version increments only when one of those changes")
    public ApiResponse<PlaybookDto> updateStep(
            @Parameter(description = "Playbook id") @PathVariable Long id,
            @Parameter(description = "Step id") @PathVariable Long stepId,
            @Valid @RequestBody PlaybookStepWriteRequest request) {
        return ApiResponse.success(playbookService.updateStep(id, stepId, request), "Playbook step updated successfully");
    }

    @GetMapping("/{id}/steps/next")
    @Operation(
            summary = "Get the next playbook step",
            description = "Returns the next incomplete step for the stage. completedStepIds is a comma-separated list of finished step ids. "
                    + "When the playbook exists and the stage is done, HTTP 200 and data.step is null. A missing playbook is HTTP 404.")
    public ApiResponse<PlaybookNextStepResponse> nextStep(
            @Parameter(description = "Playbook id") @PathVariable Long id,
            @Parameter(description = "DealStage name: PROSPECTING, QUALIFICATION, NEEDS_ANALYSIS, PROPOSAL, NEGOTIATION, WON, or LOST")
            @RequestParam(name = "stage", required = false) String stage,
            @Parameter(description = "Comma-separated step ids already completed, for example 1,2")
            @RequestParam(name = "completedStepIds", required = false) String completedStepIds) {
        return ApiResponse.success(playbookService.nextStep(id, stage, completedStepIds));
    }
}
