package com.opencrm.product.bundle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {

    Optional<ProductBundle> findByIdAndDeletedFalse(Long id);

    Optional<ProductBundle> findByUuidAndDeletedFalse(String uuid);

    List<ProductBundle> findByProductIdAndDeletedFalse(Long productId);

    Page<ProductBundle> findByDeletedFalse(Pageable pageable);

    Page<ProductBundle> findByStatusAndDeletedFalse(String status, Pageable pageable);
}
