package com.opencrm.pricing.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository for PricingProduct entity.
 */
@Repository
public interface PricingProductRepository extends JpaRepository<PricingProduct, Long> {

    Optional<PricingProduct> findByIdAndDeletedFalse(Long id);

    Optional<PricingProduct> findByExternalIdAndDeletedFalse(String externalId);

    List<PricingProduct> findByExternalIdInAndDeletedFalse(Collection<String> externalIds);

    Page<PricingProduct> findByDeletedFalse(Pageable pageable);
}
