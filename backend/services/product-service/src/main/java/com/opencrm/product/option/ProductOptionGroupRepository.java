package com.opencrm.product.option;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductOptionGroupRepository extends JpaRepository<ProductOptionGroup, Long> {

    Optional<ProductOptionGroup> findByIdAndDeletedFalse(Long id);

    List<ProductOptionGroup> findByProductIdAndDeletedFalseOrderBySortOrderAsc(Long productId);
}
