package com.opencrm.product.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByIdAndDeletedFalse(Long id);

    Optional<Product> findByUuidAndDeletedFalse(String uuid);

    /**
     * DRG-418: SKU is unique per tenant, not globally. {@code tenantId} may be null (service tokens, dev mode,
     * rows from before tenant scoping); Postgres and JPQL both treat {@code null = null} as unknown, so the
     * null-tenant bucket is matched explicitly rather than with a plain equality.
     */
    @Query("SELECT p FROM Product p WHERE p.deleted = false AND p.sku = :sku AND "
           + "((:tenantId IS NULL AND p.tenantId IS NULL) OR p.tenantId = :tenantId)")
    Optional<Product> findByTenantIdAndSkuAndDeletedFalse(@Param("tenantId") String tenantId, @Param("sku") String sku);

    Page<Product> findByDeletedFalse(Pageable pageable);

    Page<Product> findByProductTypeAndDeletedFalse(ProductType productType, Pageable pageable);

    Page<Product> findByCategoryAndDeletedFalse(ProductCategory category, Pageable pageable);

    Page<Product> findByStatusAndDeletedFalse(ProductStatus status, Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.deleted = false AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.sku) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Product> search(@Param("query") String query, Pageable pageable);

    List<Product> findByIdInAndDeletedFalse(List<Long> ids);

    /** DRG-418: uniqueness check behind DefineProduct, scoped the same way as {@link #findByTenantIdAndSkuAndDeletedFalse}. */
    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.deleted = false AND p.sku = :sku AND "
           + "((:tenantId IS NULL AND p.tenantId IS NULL) OR p.tenantId = :tenantId)")
    boolean existsByTenantIdAndSkuAndDeletedFalse(@Param("tenantId") String tenantId, @Param("sku") String sku);
}
