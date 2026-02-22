package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByExternalId(String externalId);
}
