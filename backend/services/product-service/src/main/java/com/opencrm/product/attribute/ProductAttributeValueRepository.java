package com.opencrm.product.attribute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductAttributeValueRepository extends JpaRepository<ProductAttributeValue, Long> {

    List<ProductAttributeValue> findByProductIdAndDeletedFalse(Long productId);

    Optional<ProductAttributeValue> findByProductIdAndAttributeIdAndDeletedFalse(Long productId, Long attributeId);
}
