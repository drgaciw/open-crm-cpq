package com.opencrm.pricing.customer;

import com.opencrm.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Customer management.
 */
@RestController
@RequestMapping("/api/v1/pricing-customers")
@RequiredArgsConstructor
@Tag(name = "Pricing Customers", description = "Pricing customer management endpoints")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Create a new customer.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new pricing customer", description = "Creates a new customer in the pricing system")
    public ApiResponse<CustomerDto> create(@Valid @RequestBody CustomerCreateRequest request) {
        CustomerDto customer = customerService.create(request);
        return ApiResponse.success(customer, "Customer created successfully");
    }

    /**
     * List all customers with optional filters.
     */
    @GetMapping
    @Operation(summary = "List customers", description = "Get a paginated list of customers with optional filtering")
    public ApiResponse<Page<CustomerDto>> list(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) CustomerSegment segment) {
        Page<CustomerDto> customers = customerService.list(pageable, segment);

        ApiResponse.PageInfo pageInfo = ApiResponse.PageInfo.builder()
                .page(customers.getNumber())
                .size(customers.getSize())
                .totalElements(customers.getTotalElements())
                .totalPages(customers.getTotalPages())
                .hasNext(customers.hasNext())
                .hasPrevious(customers.hasPrevious())
                .build();

        return ApiResponse.success(customers, pageInfo);
    }

    /**
     * Get a customer by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get customer by ID", description = "Retrieve a single customer by its ID")
    public ApiResponse<CustomerDto> getById(@PathVariable Long id) {
        CustomerDto customer = customerService.getById(id);
        return ApiResponse.success(customer);
    }

    /**
     * Update a customer.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update customer", description = "Update an existing customer")
    public ApiResponse<CustomerDto> update(
            @PathVariable Long id,
            @Valid @RequestBody CustomerUpdateRequest request) {
        CustomerDto customer = customerService.update(id, request);
        return ApiResponse.success(customer, "Customer updated successfully");
    }

    /**
     * Delete a customer (soft delete).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete customer", description = "Soft delete a customer")
    public void delete(@PathVariable Long id) {
        customerService.delete(id);
    }
}
