package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.ProductBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {
    List<ProductBundle> findByBundleType(String bundleType);
    List<ProductBundle> findByStatus(String status);
}
