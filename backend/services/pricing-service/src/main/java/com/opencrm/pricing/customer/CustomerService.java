package com.opencrm.pricing.customer;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing pricing customers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;

    /**
     * Create a new customer.
     */
    public CustomerDto create(CustomerCreateRequest request) {
        log.debug("Creating customer with external ID: {}", request.getExternalId());

        // Check if customer with external ID already exists
        if (customerRepository.findByExternalIdAndDeletedFalse(request.getExternalId()).isPresent()) {
            throw new BusinessException(
                    "Customer with external ID " + request.getExternalId() + " already exists",
                    "DUPLICATE_EXTERNAL_ID"
            );
        }

        Customer customer = customerMapper.toEntity(request);
        Customer saved = customerRepository.save(customer);
        log.info("Created customer with ID: {}", saved.getId());

        return customerMapper.toDto(saved);
    }

    /**
     * Get customer by ID.
     */
    @Transactional(readOnly = true)
    public CustomerDto getById(Long id) {
        log.debug("Fetching customer by ID: {}", id);

        Customer customer = customerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        return customerMapper.toDto(customer);
    }

    /**
     * Get customer by external ID.
     */
    @Transactional(readOnly = true)
    public CustomerDto getByExternalId(String externalId) {
        log.debug("Fetching customer by external ID: {}", externalId);

        Customer customer = customerRepository.findByExternalIdAndDeletedFalse(externalId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer with external ID: " + externalId));

        return customerMapper.toDto(customer);
    }

    /**
     * Update a customer.
     */
    public CustomerDto update(Long id, CustomerUpdateRequest request) {
        log.debug("Updating customer ID: {}", id);

        Customer customer = customerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        customerMapper.updateEntity(request, customer);

        Customer updated = customerRepository.save(customer);
        log.info("Updated customer ID: {}", updated.getId());

        return customerMapper.toDto(updated);
    }

    /**
     * Soft delete a customer.
     */
    public void delete(Long id) {
        log.debug("Deleting customer ID: {}", id);

        Customer customer = customerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));

        customer.softDelete();
        customerRepository.save(customer);

        log.info("Deleted customer ID: {}", id);
    }

    /**
     * List customers with optional filtering.
     */
    @Transactional(readOnly = true)
    public Page<CustomerDto> list(Pageable pageable, CustomerSegment segment) {
        log.debug("Listing customers with segment: {}", segment);

        Page<Customer> customers;

        if (segment != null) {
            customers = customerRepository.findBySegmentAndDeletedFalse(segment, pageable);
        } else {
            customers = customerRepository.findByDeletedFalse(pageable);
        }

        return customers.map(customerMapper::toDto);
    }
}
