package com.opencrm.contract.controller;

import com.opencrm.common.dto.ApiResponse;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller for subscription/asset management.
 * Authoritative subscription state and lifecycle operations.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Contract/Asset Management", description = "Subscription lifecycle and ERP integration endpoints")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription by ID")
    public ApiResponse<Subscription> getById(@PathVariable Long id) {
        return ApiResponse.success(subscriptionService.getById(id));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "List subscriptions by customer")
    public ApiResponse<List<Subscription>> getByCustomer(@PathVariable String customerId) {
        return ApiResponse.success(subscriptionService.getByCustomerId(customerId));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List subscriptions by status")
    public ApiResponse<List<Subscription>> getByStatus(@PathVariable SubscriptionStatus status) {
        return ApiResponse.success(subscriptionService.getByStatus(status));
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a pending subscription")
    public ApiResponse<Subscription> activate(@PathVariable Long id) {
        Subscription subscription = subscriptionService.activate(id);
        return ApiResponse.success(subscription, "Subscription activated");
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend an active subscription")
    public ApiResponse<Subscription> suspend(@PathVariable Long id) {
        Subscription subscription = subscriptionService.suspend(id);
        return ApiResponse.success(subscription, "Subscription suspended");
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a subscription")
    public ApiResponse<Subscription> cancel(@PathVariable Long id) {
        Subscription subscription = subscriptionService.cancel(id);
        return ApiResponse.success(subscription, "Subscription cancelled");
    }

    @PostMapping("/{id}/renew")
    @Operation(summary = "Renew an active subscription")
    public ApiResponse<Subscription> renew(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate newEndDate) {
        Subscription subscription = subscriptionService.renew(id, newEndDate);
        return ApiResponse.success(subscription, "Subscription renewed");
    }
}
