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

    Optional<Product> findBySkuAndDeletedFalse(String sku);

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

    boolean existsBySkuAndDeletedFalse(String sku);
}
