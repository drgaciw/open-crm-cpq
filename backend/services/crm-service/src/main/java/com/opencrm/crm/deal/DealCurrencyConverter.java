package com.opencrm.crm.deal;

import com.opencrm.crm.fx.CurrencyCodes;
import com.opencrm.crm.fx.FxProperties;
import com.opencrm.crm.fx.FxRate;
import com.opencrm.crm.fx.FxRateProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Freezes a deal's amount in the base currency (ADR-032).
 *
 * <p>Rules:
 * <ul>
 *   <li>D-03: callers freeze on create, when amount or currency changes, and at close (won or lost).</li>
 *   <li>D-04: with no rate, the base fields are cleared. A missing rate is never treated as 1:1.</li>
 *   <li>Same currency as base: rate 1, source {@code IDENTITY}.</li>
 * </ul>
 */
@Slf4j
@Component
public class DealCurrencyConverter {

    private final FxRateProvider rateProvider;
    private final String baseCurrency;
    private final Clock clock;

    @Autowired
    public DealCurrencyConverter(FxRateProvider rateProvider, FxProperties properties) {
        this(rateProvider, properties, Clock.systemUTC());
    }

    DealCurrencyConverter(FxRateProvider rateProvider, FxProperties properties, Clock clock) {
        this.rateProvider = rateProvider;
        this.baseCurrency = properties.baseCurrency();
        this.clock = clock;
    }

    public String baseCurrency() {
        return baseCurrency;
    }

    /** Converts at the current rate and freezes the result on the deal. */
    public void freeze(Deal deal) {
        Instant now = clock.instant();
        BigDecimal amount = deal.getAmount();
        String currency = CurrencyCodes.normalize(deal.getCurrency());

        if (amount == null || currency == null) {
            clear(deal);
            return;
        }

        Optional<FxRate> rate = currency.equals(baseCurrency)
                ? Optional.of(FxRate.identity(baseCurrency, now))
                : rateProvider.findRate(currency, baseCurrency, now);

        if (rate.isEmpty()) {
            log.warn("No FX rate {}->{} for deal {}; base amount left unconverted", currency, baseCurrency, deal.getUuid());
            clear(deal);
            return;
        }

        FxRate r = rate.get();
        deal.setBaseAmount(amount.multiply(r.rate()).setScale(2, RoundingMode.HALF_UP));
        deal.setBaseCurrency(baseCurrency);
        deal.setFxRate(r.rate());
        deal.setFxRateAsOf(r.asOf());
        deal.setFxSource(r.source());
        deal.recalculateWeightedAmounts();
    }

    /**
     * Freezes when ADR-032 D-03 calls for it on an edit: amount or currency changed, the deal is not yet converted,
     * or it was converted against a different base currency.
     */
    public void freezeIfNeeded(Deal deal, BigDecimal previousAmount, String previousCurrency) {
        boolean amountChanged = !sameAmount(previousAmount, deal.getAmount());
        boolean currencyChanged = !Objects.equals(CurrencyCodes.normalize(previousCurrency),
                CurrencyCodes.normalize(deal.getCurrency()));
        boolean notConverted = deal.getAmount() != null
                && (deal.getBaseAmount() == null || !baseCurrency.equals(deal.getBaseCurrency()));
        if (amountChanged || currencyChanged || notConverted) {
            freeze(deal);
        }
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.compareTo(b) == 0;
    }

    private static void clear(Deal deal) {
        deal.setBaseAmount(null);
        deal.setBaseCurrency(null);
        deal.setFxRate(null);
        deal.setFxRateAsOf(null);
        deal.setFxSource(null);
        deal.setBaseWeightedAmount(null);
    }
}
