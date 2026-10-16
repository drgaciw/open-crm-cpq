package com.opencrm.contract.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.dto.SubscriptionDto;
import com.opencrm.contract.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for subscription/asset management.
 * Authoritative subscription state and lifecycle operations.
 * API boundary exposes {@link SubscriptionDto} only — never the JPA entity.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Contract/Asset Management", description = "Subscription lifecycle and ERP integration endpoints")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    @Operation(summary = "List subscriptions (paginated)")
    public ApiResponse<Page<SubscriptionDto>> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<SubscriptionDto> subscriptions = subscriptionService.list(pageable);
        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(subscriptions.getNumber())
                .size(subscriptions.getSize())
                .totalElements(subscriptions.getTotalElements())
                .totalPages(subscriptions.getTotalPages())
                .hasNext(subscriptions.hasNext())
                .hasPrevious(subscriptions.hasPrevious())
                .build();
        return ApiResponse.success(subscriptions, pageInfo);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription by ID")
    public ApiResponse<SubscriptionDto> getById(@PathVariable Long id) {
        return ApiResponse.success(subscriptionService.getById(id));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "List subscriptions by customer")
    public ApiResponse<List<SubscriptionDto>> getByCustomer(@PathVariable String customerId) {
        return ApiResponse.success(subscriptionService.getByCustomerId(customerId));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List subscriptions by status")
    public ApiResponse<List<SubscriptionDto>> getByStatus(@PathVariable SubscriptionStatus status) {
        return ApiResponse.success(subscriptionService.getByStatus(status));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a pending subscription")
    public ApiResponse<SubscriptionDto> activate(@PathVariable Long id) {
        SubscriptionDto subscription = subscriptionService.activate(id);
        return ApiResponse.success(subscription, "Subscription activated");
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an active subscription")
    public ApiResponse<SubscriptionDto> suspend(@PathVariable Long id) {
        SubscriptionDto subscription = subscriptionService.suspend(id);
        return ApiResponse.success(subscription, "Subscription suspended");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription")
    public ApiResponse<SubscriptionDto> cancel(@PathVariable Long id) {
        SubscriptionDto subscription = subscriptionService.cancel(id);
        return ApiResponse.success(subscription, "Subscription cancelled");
    }

    /**
     * Date-only renewal is retired (DRG-543). The route remains and responds 410 Gone.
     * It does not extend end dates, write rate periods, or reprice.
     * Successor: {@code POST /api/v1/quotes/continuations}, applied on acceptance by
     * {@code AcceptedQuoteSubscriptionService}.
     * Rollback: restore this method and {@code Subscription.renew} from the parent commit.
     * No schema change.
     */
    @Deprecated(since = "2026-10-16", forRemoval = true)
    @PostMapping("/{id}/renew")
    @Operation(
            summary = "Renew an active subscription",
            deprecated = true,
            description = "Deprecated (DRG-543). Does not change the subscription. "
                    + "Author a priced continuation at POST /api/v1/quotes/continuations.")
    public ResponseEntity<ApiResponse<Void>> renew(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newEndDate) {
        String message = "POST /api/v1/subscriptions/" + id
                + "/renew?newEndDate=" + newEndDate
                + " is deprecated and does not change the subscription. "
                + "Renew through an accepted quote continuation: POST /api/v1/quotes/continuations. DRG-543.";
        ApiResponse<Void> body = ApiResponse.error(message, "SUBSCRIPTION_RENEW_DEPRECATED", null);
        return ResponseEntity.status(HttpStatus.GONE)
                .header("Deprecation", "true")
                .header("Link", "</api/v1/quotes/continuations>; rel=\"successor-version\"")
                .body(body);
    }
}
