package com.opencrm.crm.fx;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface FxRateRepository extends JpaRepository<FxRateEntity, Long> {

    Optional<FxRateEntity> findFirstByFromCurrencyAndToCurrencyAndAsOfLessThanEqualOrderByAsOfDesc(
            String fromCurrency, String toCurrency, Instant asOf);
}
