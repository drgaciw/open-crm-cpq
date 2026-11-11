package com.opencrm.quote.comparison;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class QuoteComparisonServiceTest {
    private final QuoteComparisonRepository quotes = mock(QuoteComparisonRepository.class);
    private final QuotePrimarySelectionRepository selections = mock(QuotePrimarySelectionRepository.class);
    private final QuoteComparisonService service = new QuoteComparisonService(quotes, selections);
    private Quote anchor;

    @BeforeEach void setup() {
        user("workforce");
        anchor = quote(1L);
        when(quotes.findByIdAndTenantIdAndDeletedFalse(1L, "workforce")).thenReturn(Optional.of(anchor));
        when(quotes.lockAnchor(1L, "workforce")).thenReturn(Optional.of(anchor));
        when(quotes.alternatives(eq("workforce"), eq("deal-1"), any(Pageable.class)))
                .thenReturn(List.of(anchor, quote(2L)));
        when(selections.selectedQuote("workforce", "deal-1")).thenReturn(Optional.empty());
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void comparesPersistedTotalsAttributesAndNeverReportsPlaceholderMargin() {
        anchor.setPricingWaterfallJson("{\"metrics\":{\"estimatedMarginPct\":40.0}}");
        anchor.setLineItems(new ArrayList<>(List.of(QuoteLineItem.builder().productExternalId("SKU")
                .productName("Named seats").quantity(25).billingFrequency("MONTHLY").unitOfMeasure("SEAT").build(),
                QuoteLineItem.builder().productExternalId("DELETED").deleted(true).build())));
        var comparison = service.compare(1L);
        assertThat(comparison.quotes()).hasSize(2);
        assertThat(comparison.sameCurrency()).isTrue();
        assertThat(comparison.quotes().getFirst().totalAmount()).isEqualByComparingTo("1100");
        assertThat(comparison.quotes().getFirst().durationInMonths()).isEqualTo(36);
        assertThat(comparison.quotes().getFirst().lines()).singleElement().satisfies(line -> {
            assertThat(line.productExternalId()).isEqualTo("SKU");
            assertThat(line.quantity()).isEqualTo(25);
            assertThat(line.billingFrequency()).isEqualTo("MONTHLY");
        });
        assertThat(comparison.quotes()).allSatisfy(quote -> {
            assertThat(quote.margin().status()).isEqualTo("UNAVAILABLE");
            assertThat(quote.margin().value()).isNull();
        });
        verify(selections).selectedQuote("workforce", "deal-1");
        verifyNoMoreInteractions(selections);
    }

    @Test void markPrimaryPersistsPreferenceAndAnyAlternativeCanReplaceItWithoutChangingQuote() {
        when(selections.selectedQuote("workforce", "deal-1")).thenReturn(Optional.of(1L));
        assertThat(service.markPrimary(1L).primaryQuoteId()).isEqualTo(1L);
        verify(selections).select("workforce", "deal-1", 1L, "seller");
        assertThat(anchor.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(anchor.getTotalAmount()).isEqualByComparingTo("1100");
        Quote second = quote(2L);
        second.setStatus(QuoteStatus.ACCEPTED); // Preference is independent of lifecycle status.
        when(quotes.lockAnchor(2L, "workforce")).thenReturn(Optional.of(second));
        when(quotes.alternatives(eq("workforce"), eq("deal-1"), any(Pageable.class)))
                .thenReturn(List.of(anchor, second));
        when(selections.selectedQuote("workforce", "deal-1")).thenReturn(Optional.of(2L));
        var changed = service.markPrimary(2L);
        assertThat(changed.primaryQuoteId()).isEqualTo(2L);
        assertThat(changed.quotes().get(1).status()).isEqualTo(QuoteStatus.ACCEPTED);
        verify(selections).select("workforce", "deal-1", 2L, "seller");
        assertThat(second.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
    }

    @Test void staleDeletedOrMovedSelectionDoesNotAppearAsPrimary() {
        when(selections.selectedQuote("workforce", "deal-1")).thenReturn(Optional.of(99L));
        assertThat(service.compare(1L).primaryQuoteId()).isNull();
    }

    @Test void mixedCurrenciesAreRetainedWithExplicitNonComparableSignal() {
        Quote euro = quote(2L); euro.setCurrency("EUR");
        when(quotes.alternatives(eq("workforce"), eq("deal-1"), any(Pageable.class)))
                .thenReturn(List.of(anchor, euro));
        assertThat(service.compare(1L).sameCurrency()).isFalse();
        assertThat(service.compare(1L).quotes()).extracting(QuoteComparisonDto.Alternative::currency)
                .containsExactly("USD", "EUR");
    }

    @Test void opportunityAndCustomerScopeAreRequiredBeforeWriting() {
        anchor.setDealExternalId(null);
        assertThatThrownBy(() -> service.markPrimary(1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo("QUOTE_COMPARISON_REQUIRES_OPPORTUNITY"));
        verifyNoInteractions(selections);
        anchor.setDealExternalId("deal-1");
        Quote foreignCustomer = quote(2L); foreignCustomer.setCustomerExternalId("another-account");
        when(quotes.alternatives(eq("workforce"), eq("deal-1"), any(Pageable.class)))
                .thenReturn(List.of(anchor, foreignCustomer));
        assertThatThrownBy(() -> service.markPrimary(1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo("QUOTE_COMPARISON_CUSTOMER_MISMATCH"));
        verifyNoInteractions(selections);
    }

    @Test void refusesOversizedGroupRatherThanSilentlyOmittingAnAlternative() {
        when(quotes.alternatives(eq("workforce"), eq("deal-1"), any(Pageable.class)))
                .thenReturn(LongStream.range(1, 52).mapToObj(this::quote).toList());
        assertThatThrownBy(() -> service.compare(1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo("QUOTE_COMPARISON_LIMIT"));
    }

    @Test void foreignDeletedAndLegacyNullTenantQuotesAreNotAdopted() {
        when(quotes.findByIdAndTenantIdAndDeletedFalse(1L, "workforce")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.compare(1L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(selections);
    }

    @Test void missingOrServiceWorkforceIdentityIsRefusedBeforeLookup() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.compare(1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        user(null);
        assertThatThrownBy(() -> service.markPrimary(1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        Jwt jwt = Jwt.withTokenValue("app").header("alg", "none").subject("service")
                .claim("idtyp", "app").claim("tid", "workforce").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        assertThatThrownBy(() -> service.markPrimary(1L)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(quotes, selections);
    }

    private Quote quote(long id) {
        return Quote.builder().id(id).quoteNumber("Q-" + id).name("Alternative " + id).tenantId("workforce")
                .dealExternalId("deal-1").customerExternalId("account-1").currency("USD").durationInMonths(36)
                .subtotal(new BigDecimal("1000")).totalTax(new BigDecimal("100"))
                .totalAmount(new BigDecimal("1100")).build();
    }

    static void user(String tenant) {
        Jwt.Builder builder = Jwt.withTokenValue("seller").header("alg", "none").subject("seller").claim("scp", "access");
        if (tenant != null) builder.claim("tid", tenant);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(), List.of()));
    }
}
