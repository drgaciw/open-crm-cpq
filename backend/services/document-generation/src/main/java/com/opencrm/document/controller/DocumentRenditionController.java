package com.opencrm.document.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.document.dto.*;
import com.opencrm.document.service.DocumentRenditionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/documents/renditions")
@Tag(name = "Document Renditions", description = "Generation jobs with immutable selected-clause snapshots")
public class DocumentRenditionController {
    private final DocumentRenditionService service;
    @PostMapping @Operation(summary = "Select and snapshot approved clauses, then queue document generation")
    public ApiResponse<DocumentRenditionDto> generate(@Valid @RequestBody GenerateRenditionRequest request) {
        return ApiResponse.success(service.generate(request));
    }
    @GetMapping("/{id}") @Operation(summary = "Fetch rendition status and exact selected clause text and versions")
    public ApiResponse<DocumentRenditionDto> get(@PathVariable Long id) { return ApiResponse.success(service.get(id)); }
}
