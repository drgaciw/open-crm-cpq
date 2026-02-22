package com.opencrm.pricingairules.domain.repository;

import com.opencrm.pricingairules.domain.model.Quote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {
    Optional<Quote> findByExternalId(String externalId);
    List<Quote> findByStatus(String status);
}

