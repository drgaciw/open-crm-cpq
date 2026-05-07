package com.opencrm.pricing.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Repository for PricingProduct entity.
 */
@Repository
public interface PricingProductRepository extends JpaRepository<PricingProduct, Long> {

    Optional<PricingProduct> findByIdAndDeletedFalse(Long id);

    Optional<PricingProduct> findByExternalIdAndDeletedFalse(String externalId);

    List<PricingProduct> findByExternalIdInAndDeletedFalse(List<String> externalIds);

    Page<PricingProduct> findByDeletedFalse(Pageable pageable);
}
