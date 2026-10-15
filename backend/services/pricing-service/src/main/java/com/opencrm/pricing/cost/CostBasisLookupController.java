package com.opencrm.pricing.cost;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Read-only lookup of the usable cost basis of a product (review finding M3), through the same
 * {@link CostBasisLookup} port the margin calculation uses, so the answer is exactly what pricing would use.
 *
 * <p><b>Role: {@code Admin}, as for the import</b> ({@link CostBasisSecurityConfiguration}, which already covers
 * {@code /api/v1/cost-basis/**}). The ADR-016 role table ({@code CPQ.User}, {@code DealDesk}, {@code SalesVP},
 * {@code CFO}, {@code Admin}, {@code Internal.Invoke}) has no read-only Finance or cost-viewer role. Per-unit cost is
 * commercially sensitive and is not shown to sellers ({@code CPQ.User}); the approval roles see margin, not cost, in the
 * quote, and ADR-016 decision 7 keeps them approval tiers, not data access. Opening the lookup to them would be a new
 * access policy for the owner, not a fix. The roles stay coarse until the fine-grained authorization ADR.
 */
@RestController
@RequestMapping(CostBasisLookupController.PATH)
@Tag(name = "Cost Basis", description = "Finance cost basis: export ingestion, audit trail and lookup")
public class CostBasisLookupController {

    static final String PATH = "/api/v1/cost-basis/products";

    private final CostBasisLookup lookup;
    private final Clock clock;

    @Autowired
    public CostBasisLookupController(CostBasisLookup lookup) {
        this(lookup, Clock.systemUTC());
    }

    CostBasisLookupController(CostBasisLookup lookup, Clock clock) {
        this.lookup = lookup;
        this.clock = clock;
    }

    /**
     * The cost a period starting {@code effectiveOn}, priced on {@code pricedOn}, would use. Both default to today (UTC),
     * each independently. 404 {@code COST_BASIS_NOT_FOUND} when there is no usable cost: no row on or before
     * {@code effectiveOn}, a basis stale on {@code pricedOn}, or components that disagree on unit or currency.
     */
    @GetMapping("/{productExternalId}")
    @Operation(summary = "Look up the usable cost basis of a product",
            description = "effectiveOn selects the rows; pricedOn judges freshness (two quarters). Both default to "
                    + "today, UTC. 404 COST_BASIS_NOT_FOUND when margin would fail closed.")
    public ApiResponse<CostBasisView> find(
            @PathVariable String productExternalId,
            @Parameter(description = "Selects the rows: the newest effective on or before it (ISO date)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveOn,
            @Parameter(description = "Judges freshness: the basis current on it must not be stale (ISO date)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate pricedOn) {
        LocalDate today = LocalDate.now(clock);
        LocalDate selectOn = effectiveOn == null ? today : effectiveOn;
        LocalDate judgeOn = pricedOn == null ? today : pricedOn;
        UnitCost cost = lookup.find(productExternalId, selectOn, judgeOn).orElseThrow(() -> new BusinessException(
                "No usable cost basis for " + productExternalId + " effective on " + selectOn + ", priced on "
                        + judgeOn + ": no cost row, a stale basis, or components that disagree on unit or currency",
                HttpStatus.NOT_FOUND, "COST_BASIS_NOT_FOUND"));
        return ApiResponse.success(CostBasisView.of(cost, selectOn, judgeOn));
    }

    /** A date that is not ISO {@code YYYY-MM-DD}: 400, rather than the module handler's generic 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiResponse<Void>> badDate(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(ApiResponse.error(
                "Parameter " + e.getName() + " must be an ISO date (YYYY-MM-DD)", "COST_BASIS_DATE_INVALID",
                MDC.get("traceId")));
    }
}
