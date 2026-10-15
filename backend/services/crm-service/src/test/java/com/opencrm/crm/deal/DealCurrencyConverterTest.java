package com.opencrm.crm.deal;

import com.opencrm.crm.fx.FxProperties;
import com.opencrm.crm.fx.FxRate;
import com.opencrm.crm.fx.FxRateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR-032: frozen base amount per deal. */
@ExtendWith(MockitoExtension.class)
class DealCurrencyConverterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T09:00:00Z");
    private static final Instant RATE_AS_OF = Instant.parse("2026-10-14T00:00:00Z");

    @Mock
    private FxRateProvider rateProvider;

    private DealCurrencyConverter converter;

    @BeforeEach
    void setUp() {
        converter = new DealCurrencyConverter(rateProvider, new FxProperties("USD"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Deal deal(String amount, String currency, int probability) {
        return Deal.builder()
                .uuid("deal-1")
                .name("Deal")
                .amount(amount == null ? null : new BigDecimal(amount))
                .currency(currency)
                .probability(probability)
                .build();
    }

    @Test
    @DisplayName("same currency as base: rate 1, source IDENTITY, no rate lookup")
    void identityWhenDealIsInBaseCurrency() {
        Deal deal = deal("1000.00", "usd", 60);

        converter.freeze(deal);

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1000.00");
        assertThat(deal.getBaseCurrency()).isEqualTo("USD");
        assertThat(deal.getFxRate()).isEqualByComparingTo("1");
        assertThat(deal.getFxSource()).isEqualTo(FxRate.SOURCE_IDENTITY);
        assertThat(deal.getFxRateAsOf()).isEqualTo(NOW);
        assertThat(deal.getBaseWeightedAmount()).isEqualByComparingTo("600.00");
        verify(rateProvider, never()).findRate(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("other currency: converts with the latest rate and records rate, as-of and source")
    void convertsWithRate() {
        when(rateProvider.findRate("EUR", "USD", NOW))
                .thenReturn(Optional.of(new FxRate("EUR", "USD", new BigDecimal("1.0850000000"), RATE_AS_OF, "MANUAL")));
        Deal deal = deal("1000000.00", "EUR", 80);

        converter.freeze(deal);

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1085000.00");
        assertThat(deal.getBaseCurrency()).isEqualTo("USD");
        assertThat(deal.getFxRate()).isEqualByComparingTo("1.085");
        assertThat(deal.getFxRateAsOf()).isEqualTo(RATE_AS_OF);
        assertThat(deal.getFxSource()).isEqualTo("MANUAL");
        assertThat(deal.getBaseWeightedAmount()).isEqualByComparingTo("868000.00");
    }

    @Test
    @DisplayName("rounds the base amount half-up to 2 decimals")
    void roundsHalfUp() {
        when(rateProvider.findRate("JPY", "USD", NOW))
                .thenReturn(Optional.of(new FxRate("JPY", "USD", new BigDecimal("0.0066666667"), RATE_AS_OF, "MANUAL")));
        Deal deal = deal("12345", "JPY", 10);

        converter.freeze(deal);

        // 12345 * 0.0066666667 = 82.3000004... -> 82.30
        assertThat(deal.getBaseAmount()).isEqualByComparingTo("82.30");
        assertThat(deal.getBaseAmount().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("D-04: missing rate leaves the deal unconverted, never 1:1")
    void missingRateIsNotOneToOne() {
        when(rateProvider.findRate("GBP", "USD", NOW)).thenReturn(Optional.empty());
        Deal deal = deal("500.00", "GBP", 20);
        deal.setBaseAmount(new BigDecimal("999.00"));
        deal.setBaseCurrency("USD");
        deal.setFxRate(BigDecimal.ONE);

        converter.freeze(deal);

        assertThat(deal.getBaseAmount()).isNull();
        assertThat(deal.getBaseCurrency()).isNull();
        assertThat(deal.getFxRate()).isNull();
        assertThat(deal.getFxRateAsOf()).isNull();
        assertThat(deal.getFxSource()).isNull();
        assertThat(deal.getBaseWeightedAmount()).isNull();
    }

    @Test
    @DisplayName("freeze at an explicit instant looks up the rate as of that instant")
    void freezeUsesSuppliedInstant() {
        Instant acceptedAt = Instant.parse("2026-10-01T00:00:00Z");
        when(rateProvider.findRate("EUR", "USD", acceptedAt))
                .thenReturn(Optional.of(new FxRate("EUR", "USD", new BigDecimal("1.05"), acceptedAt.minusSeconds(3600), "MANUAL")));
        Deal deal = deal("1000.00", "EUR", 100);

        converter.freeze(deal, acceptedAt);

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1050.00");
        assertThat(deal.getFxRateAsOf()).isEqualTo(acceptedAt.minusSeconds(3600));
        verify(rateProvider, never()).findRate(anyString(), anyString(), eq(NOW));
    }

    @Test
    @DisplayName("a null instant falls back to the converter clock")
    void nullInstantUsesClock() {
        when(rateProvider.findRate("EUR", "USD", NOW))
                .thenReturn(Optional.of(new FxRate("EUR", "USD", new BigDecimal("1.1"), RATE_AS_OF, "MANUAL")));
        Deal deal = deal("1000.00", "EUR", 10);

        converter.freeze(deal, null);

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1100.00");
        verify(rateProvider).findRate("EUR", "USD", NOW);
    }

    @Test
    @DisplayName("identity freeze records the supplied instant as the rate as-of")
    void identityUsesSuppliedInstant() {
        Instant acceptedAt = Instant.parse("2026-10-01T00:00:00Z");
        Deal deal = deal("1000.00", "USD", 100);

        converter.freeze(deal, acceptedAt);

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1000.00");
        assertThat(deal.getFxRateAsOf()).isEqualTo(acceptedAt);
        assertThat(deal.getFxSource()).isEqualTo(FxRate.SOURCE_IDENTITY);
        verify(rateProvider, never()).findRate(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("no amount: nothing to convert")
    void noAmount() {
        Deal deal = deal(null, "EUR", 10);

        converter.freeze(deal);

        assertThat(deal.getBaseAmount()).isNull();
        verify(rateProvider, never()).findRate(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("freezeIfNeeded: unchanged, already converted deal keeps its frozen rate")
    void unchangedDealKeepsFrozenRate() {
        Deal deal = deal("1000.00", "EUR", 40);
        deal.setBaseAmount(new BigDecimal("1100.00"));
        deal.setBaseCurrency("USD");
        deal.setFxRate(new BigDecimal("1.1"));

        converter.freezeIfNeeded(deal, new BigDecimal("1000.0"), "eur");

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1100.00");
        assertThat(deal.getFxRate()).isEqualByComparingTo("1.1");
        verify(rateProvider, never()).findRate(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("freezeIfNeeded: amount change re-freezes at the current rate")
    void amountChangeRefreezes() {
        when(rateProvider.findRate("EUR", "USD", NOW))
                .thenReturn(Optional.of(new FxRate("EUR", "USD", new BigDecimal("1.2"), RATE_AS_OF, "MANUAL")));
        Deal deal = deal("2000.00", "EUR", 40);
        deal.setBaseAmount(new BigDecimal("1100.00"));
        deal.setBaseCurrency("USD");
        deal.setFxRate(new BigDecimal("1.1"));

        converter.freezeIfNeeded(deal, new BigDecimal("1000.00"), "EUR");

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("2400.00");
        assertThat(deal.getFxRate()).isEqualByComparingTo("1.2");
    }

    @Test
    @DisplayName("freezeIfNeeded: currency change re-freezes")
    void currencyChangeRefreezes() {
        Deal deal = deal("1000.00", "USD", 40);
        deal.setBaseAmount(new BigDecimal("1100.00"));
        deal.setBaseCurrency("USD");
        deal.setFxRate(new BigDecimal("1.1"));

        converter.freezeIfNeeded(deal, new BigDecimal("1000.00"), "EUR");

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1000.00");
        assertThat(deal.getFxSource()).isEqualTo(FxRate.SOURCE_IDENTITY);
    }

    @Test
    @DisplayName("freezeIfNeeded: a deal still unconverted is retried on any edit")
    void unconvertedDealIsRetried() {
        when(rateProvider.findRate("EUR", "USD", NOW))
                .thenReturn(Optional.of(new FxRate("EUR", "USD", new BigDecimal("1.1"), RATE_AS_OF, "MANUAL")));
        Deal deal = deal("1000.00", "EUR", 40);

        converter.freezeIfNeeded(deal, new BigDecimal("1000.00"), "EUR");

        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1100.00");
    }

    @Test
    @DisplayName("freezeIfNeeded: a deal frozen against another base currency is re-based")
    void otherBaseIsRebased() {
        Deal deal = deal("1000.00", "USD", 40);
        deal.setBaseAmount(new BigDecimal("900.00"));
        deal.setBaseCurrency("EUR");

        converter.freezeIfNeeded(deal, new BigDecimal("1000.00"), "USD");

        assertThat(deal.getBaseCurrency()).isEqualTo("USD");
        assertThat(deal.getBaseAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("probability change recomputes the base weighted amount without changing the frozen rate")
    void weightedAmountFollowsProbability() {
        Deal deal = deal("1000.00", "USD", 10);
        converter.freeze(deal);

        deal.advanceStage(); // PROSPECTING -> QUALIFICATION, probability 20
        deal.recalculateWeightedAmounts();

        assertThat(deal.getBaseWeightedAmount()).isEqualByComparingTo("200.00");
        assertThat(deal.getFxRate()).isEqualByComparingTo("1");
    }
}
