package com.opencrm.product.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CatalogVersionProductRepository extends JpaRepository<CatalogVersionProduct, Long> {

    List<CatalogVersionProduct> findByCatalogVersionIdOrderBySkuAsc(Long catalogVersionId);

    Optional<CatalogVersionProduct> findByCatalogVersionIdAndProductId(Long catalogVersionId, Long productId);

    Optional<CatalogVersionProduct> findByCatalogVersionIdAndSku(Long catalogVersionId, String sku);

    boolean existsByCatalogVersionIdAndSku(Long catalogVersionId, String sku);
}
