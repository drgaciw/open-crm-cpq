package com.opencrm.pricing.cost;

import com.opencrm.common.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Finance cost basis ingestion (DRG-526 / BC-03-13, F-2 export contract). Admin only: see
 * {@link CostBasisSecurityConfiguration}. DTOs only at this boundary (ADR-006).
 */
@RestController
@RequestMapping(CostBasisImportController.PATH)
@Tag(name = "Cost Basis", description = "Finance cost basis: export ingestion, audit trail and lookup")
public class CostBasisImportController {

    static final String PATH = "/api/v1/cost-basis/imports";

    private final CostBasisIngestionService ingestion;
    private final CostBasisProperties properties;

    public CostBasisImportController(CostBasisIngestionService ingestion, CostBasisProperties properties) {
        this.ingestion = ingestion;
        this.properties = properties;
    }

    /**
     * Ingests one F-2 export, sent as the raw CSV body. Rows are rejected one by one and the rest loads; re-sending the
     * same version is a no-op. A body over {@code opencrm.pricing.cost-basis.max-bytes} (413), a missing header (400),
     * too many rows, or more than one version or approver (422) refuses the whole file. The body is read as UTF-8, and
     * never further than one byte past the limit.
     */
    @PostMapping(consumes = {"text/csv", "text/plain"})
    @Operation(summary = "Ingest a Finance cost export (F-2 CSV)",
            description = "Idempotent by version. Returns the audit entry, every rejected row, and the scope of the "
                    + "billing unit check. 413 COST_EXPORT_BODY_TOO_LARGE over max-bytes.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = {
                    @Content(mediaType = "text/csv", schema = @Schema(type = "string")),
                    @Content(mediaType = "text/plain", schema = @Schema(type = "string"))}))
    public ApiResponse<CostBasisImportReport> ingest(HttpServletRequest request) throws IOException {
        long maxBytes = properties.getMaxBytes().toBytes();
        long declared = request.getContentLengthLong();
        if (declared > maxBytes) {
            throw ingestion.bodyTooLarge(declared);
        }
        Optional<String> csv = readAtMost(request.getInputStream(), maxBytes);
        if (csv.isEmpty()) {
            throw ingestion.bodyTooLarge(maxBytes + 1);
        }
        CostBasisImportReport report = ingestion.ingest(csv.get());
        return ApiResponse.success(report, report.audit().rejected() == 0
                ? "Cost export ingested" : "Cost export ingested with rejected rows");
    }

    /** The body as UTF-8, or empty when it holds more than {@code maxBytes}; reads at most {@code maxBytes + 1}. */
    static Optional<String> readAtMost(InputStream body, long maxBytes) throws IOException {
        int cap = (int) Math.min(maxBytes + 1, Integer.MAX_VALUE - 8L);
        byte[] bytes = body.readNBytes(cap);
        return bytes.length > maxBytes ? Optional.empty() : Optional.of(new String(bytes, StandardCharsets.UTF_8));
    }

    /** The audit trail of ingestions, newest first. */
    @GetMapping
    @Operation(summary = "List cost export ingestions", description = "Audit entries, newest first.")
    public ApiResponse<Page<CostBasisImportSummary>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<CostBasisImportSummary> page = ingestion.list(pageable);
        return ApiResponse.success(page, ApiResponse.PageInfo.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build());
    }
}
