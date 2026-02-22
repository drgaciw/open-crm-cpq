package com.opencrm.pricing.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Customer entity.
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * Find customer by external ID (excluding deleted).
     */
    Optional<Customer> findByExternalIdAndDeletedFalse(String externalId);

    /**
     * Find customer by ID (excluding deleted).
     */
    Optional<Customer> findByIdAndDeletedFalse(Long id);

    /**
     * Find all non-deleted customers.
     */
    Page<Customer> findByDeletedFalse(Pageable pageable);

    /**
     * Find customers by segment (excluding deleted).
     */
    Page<Customer> findBySegmentAndDeletedFalse(CustomerSegment segment, Pageable pageable);
}
