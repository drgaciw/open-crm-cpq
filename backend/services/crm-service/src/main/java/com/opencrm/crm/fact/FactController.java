package com.opencrm.crm.fact;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Evidence-priced facts for one contact or organization (ADR-028 / DRG-512).
 * Routes sit under the existing contact and organization gateway prefixes.
 * The tenant-wide inbox is a later slice. Score and weight are not request fields.
 */
@RestController
@Validated
@RequiredArgsConstructor
@Tag(name = "Fact ledger", description = "Evidence-priced facts and per-subject proposals")
public class FactController {

    private final FactLedgerService factLedgerService;

    @PostMapping("/api/v1/contacts/{subjectId}/facts")
    @Operation(summary = "Propose a fact about a contact")
    public ResponseEntity<ApiResponse<FactResponse>> proposeContact(
            @PathVariable long subjectId,
            @RequestParam @NotBlank String tenant,
            @Valid @RequestBody ProposeFactRequest request) {
        return propose(FactSubjectType.CONTACT, subjectId, tenant, request);
    }

    @GetMapping("/api/v1/contacts/{subjectId}/facts/proposals")
    @Operation(summary = "List contact proposals", description = "PROPOSED and UNRESOLVED facts. POSSIBLE provenance is omitted.")
    public ApiResponse<List<FactResponse>> listContactProposals(
            @PathVariable long subjectId,
            @RequestParam @NotBlank String tenant) {
        return ApiResponse.success(factLedgerService.listProposals(FactSubjectType.CONTACT, subjectId, tenant));
    }

    @PostMapping("/api/v1/contacts/{subjectId}/facts/{factId}/accept")
    @Operation(summary = "Accept a contact proposal")
    public ApiResponse<FactResponse> acceptContact(
            @PathVariable long subjectId,
            @PathVariable long factId,
            @RequestParam @NotBlank String tenant) {
        return ApiResponse.success(
                factLedgerService.accept(FactSubjectType.CONTACT, subjectId, factId, tenant),
                "Fact accepted");
    }

    @PostMapping("/api/v1/contacts/{subjectId}/facts/{factId}/dismiss")
    @Operation(summary = "Dismiss a contact proposal")
    public ApiResponse<FactResponse> dismissContact(
            @PathVariable long subjectId,
            @PathVariable long factId,
            @RequestParam @NotBlank String tenant) {
        return ApiResponse.success(
                factLedgerService.dismiss(FactSubjectType.CONTACT, subjectId, factId, tenant),
                "Fact dismissed");
    }

    @PostMapping("/api/v1/organizations/{subjectId}/facts")
    @Operation(summary = "Propose a fact about an organization")
    public ResponseEntity<ApiResponse<FactResponse>> proposeOrganization(
            @PathVariable long subjectId,
            @RequestParam @NotBlank String tenant,
            @Valid @RequestBody ProposeFactRequest request) {
        return propose(FactSubjectType.ORGANIZATION, subjectId, tenant, request);
    }

    @GetMapping("/api/v1/organizations/{subjectId}/facts/proposals")
    @Operation(summary = "List organization proposals")
    public ApiResponse<List<FactResponse>> listOrganizationProposals(
            @PathVariable long subjectId,
            @RequestParam @NotBlank String tenant) {
        return ApiResponse.success(factLedgerService.listProposals(FactSubjectType.ORGANIZATION, subjectId, tenant));
    }

    @PostMapping("/api/v1/organizations/{subjectId}/facts/{factId}/accept")
    @Operation(summary = "Accept an organization proposal")
    public ApiResponse<FactResponse> acceptOrganization(
            @PathVariable long subjectId,
            @PathVariable long factId,
            @RequestParam @NotBlank String tenant) {
        return ApiResponse.success(
                factLedgerService.accept(FactSubjectType.ORGANIZATION, subjectId, factId, tenant),
                "Fact accepted");
    }

    @PostMapping("/api/v1/organizations/{subjectId}/facts/{factId}/dismiss")
    @Operation(summary = "Dismiss an organization proposal")
    public ApiResponse<FactResponse> dismissOrganization(
            @PathVariable long subjectId,
            @PathVariable long factId,
            @RequestParam @NotBlank String tenant) {
        return ApiResponse.success(
                factLedgerService.dismiss(FactSubjectType.ORGANIZATION, subjectId, factId, tenant),
                "Fact dismissed");
    }

    private ResponseEntity<ApiResponse<FactResponse>> propose(
            FactSubjectType subjectType,
            long subjectId,
            String tenant,
            ProposeFactRequest request) {
        FactResponse fact = factLedgerService.propose(subjectType, subjectId, tenant, request);
        if (!fact.isStored()) {
            return ResponseEntity.ok(ApiResponse.success(fact, "Fact was not stored"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(fact, "Fact recorded"));
    }

    /**
     * A score or an evidence weight is not a property of the body. Jackson reports that
     * as an unreadable message. Handle it here so the shared advice does not turn it into 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException ex) {
        String detail = ex.getMostSpecificCause() == null ? null : ex.getMostSpecificCause().getMessage();
        boolean clientScore = detail != null && (detail.contains("score") || detail.contains("weight"));
        String code = clientScore ? "FACT_SCORE_NOT_CLIENT_SUPPLIED" : "FACT_BODY_UNREADABLE";
        String message = clientScore
                ? "The service computes the score and evidence weights. Do not send them."
                : "Fact request body could not be read";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(message, code, null));
    }
}
