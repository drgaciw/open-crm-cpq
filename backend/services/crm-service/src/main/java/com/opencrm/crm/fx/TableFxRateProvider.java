package com.opencrm.crm.fx;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/** {@link FxRateProvider} backed by the {@code fx_rates} table. Direct pairs only; no inverse or cross rates. */
@Component
@RequiredArgsConstructor
public class TableFxRateProvider implements FxRateProvider {

    private final FxRateRepository repository;

    @Override
    public Optional<FxRate> findRate(String fromCurrency, String toCurrency, Instant asOf) {
        return repository
                .findFirstByFromCurrencyAndToCurrencyAndAsOfLessThanEqualOrderByAsOfDesc(fromCurrency, toCurrency, asOf)
                .map(e -> new FxRate(e.getFromCurrency(), e.getToCurrency(), e.getRate(), e.getAsOf(), e.getSource()));
    }
}
