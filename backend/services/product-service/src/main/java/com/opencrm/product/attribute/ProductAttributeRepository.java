package com.opencrm.product.attribute;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductAttributeRepository extends JpaRepository<ProductAttribute, Long> {

    Optional<ProductAttribute> findByIdAndDeletedFalse(Long id);

    Optional<ProductAttribute> findByNameAndDeletedFalse(String name);

    Page<ProductAttribute> findByDeletedFalse(Pageable pageable);

    boolean existsByNameAndDeletedFalse(String name);
}
