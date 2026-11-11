package com.opencrm.crm.audit;

import com.opencrm.audit.AuditTrailService;
import com.opencrm.audit.dto.AuditEntryDto;
import com.opencrm.audit.dto.SearchAuditTrailQuery;
import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * SearchAuditTrail (DRG-309 / BC-20-01): read-only search over the shared, hash-chained audit trail
 * ({@code com.opencrm.audit}). Lives here because crm-service already has a database connection and REST layer and
 * there is no dedicated audit-trail service yet (see the PR description for the full placement rationale) — this
 * controller does not touch any CRM domain table, it only delegates to {@link AuditTrailService}. Writing entries
 * ({@code AuditTrailService#append}) is an in-process library API for now; no service is wired to call it yet
 * (future integration work, out of scope for this story).
 */
@RestController
@RequestMapping("/api/v1/audit-entries")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "Search the append-only, hash-chained audit trail")
public class AuditTrailController {

    private final AuditTrailService auditTrailService;

    /**
     * SearchAuditTrail: filter by tenant, aggregate (type and/or id), principal and an occurred-at time range.
     * Every parameter is optional.
     */
    @GetMapping
    @Operation(summary = "Search the audit trail",
            description = "Filter audit entries by tenant, aggregate, principal and time range")
    public ApiResponse<List<AuditEntryDto>> search(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) String principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<AuditEntryDto> result = auditTrailService.search(
                new SearchAuditTrailQuery(tenantId, aggregateType, aggregateId, principal, from, to), pageable);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .hasNext(result.hasNext())
                .hasPrevious(result.hasPrevious())
                .build();

        return ApiResponse.success(result.getContent(), pageInfo);
    }
}
