package com.opencrm.pricing.pricebook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for PriceBook entity.
 */
@Repository
public interface PriceBookRepository extends JpaRepository<PriceBook, Long> {

    Optional<PriceBook> findByIdAndDeletedFalse(Long id);

    Page<PriceBook> findByDeletedFalse(Pageable pageable);

    Page<PriceBook> findByRegionAndDeletedFalse(String region, Pageable pageable);

    Optional<PriceBook> findByRegionAndDeletedFalse(String region);

    Optional<PriceBook> findByIsDefaultTrueAndDeletedFalse();
}
