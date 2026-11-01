package com.opencrm.quote.lifecycle;

import com.opencrm.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Lifecycle-change API for intent AMEND.
 *
 * <p>Authentication is the shared JWT chain in libs/security. The tenant id is taken from the
 * token, never from the body. Rate limiting is the gateway Redis RequestRateLimiter: 50 requests
 * per second replenish and a burst of 100 ({@code RATE_LIMIT_REPLENISH_RATE} /
 * {@code RATE_LIMIT_BURST_CAPACITY}). This controller is disabled by default. Do not enable it or
 * route it through the gateway until pricing and tenant/contract-scoped target ownership are
 * implemented and the apply boundary atomically rechecks observed revisions.
 */
@RestController
@RequestMapping("/api/v1/lifecycle-changes")
@ConditionalOnProperty(name = "opencrm.lifecycle-change.enabled", havingValue = "true")
public class LifecycleChangeController {

    private final LifecycleChangeService service;

    public LifecycleChangeController(LifecycleChangeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LifecycleChangeResponse>> create(
            @Valid @RequestBody CreateLifecycleChangeRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        LifecycleChangeResult result = service.create(request, idempotencyKey);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.success(result.body(), result.replayed()
                        ? "Lifecycle change replayed" : "Lifecycle change created"));
    }

    @GetMapping("/{lifecycleChangeId}")
    public ApiResponse<LifecycleChangeResponse> get(@PathVariable UUID lifecycleChangeId) {
        return ApiResponse.success(service.get(lifecycleChangeId));
    }

    @PostMapping("/{lifecycleChangeId}:price")
    public ApiResponse<LifecycleChangeResponse> price(@PathVariable UUID lifecycleChangeId,
                                                      @Valid @RequestBody PriceLifecycleChangeRequest request) {
        return ApiResponse.success(service.price(lifecycleChangeId, request).body(), "Lifecycle change priced");
    }

    @PostMapping("/{lifecycleChangeId}:submit")
    public ResponseEntity<ApiResponse<LifecycleChangeResponse>> submit(
            @PathVariable UUID lifecycleChangeId,
            @Valid @RequestBody VersionedCommand request) {
        LifecycleChangeResult result = service.submit(lifecycleChangeId, request);
        return ResponseEntity.ok()
                .header("Idempotent-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.success(result.body(), result.replayed()
                        ? "Lifecycle change submit replayed" : "Lifecycle change submitted"));
    }
}
