package com.opencrm.quote.comparison;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ComparisonServiceTest {
    @Mock QuoteRepository repository;
    @Mock QuoteMapper mapper;
    @Mock EntityManager entityManager;
    @InjectMocks ComparisonService service;
    Quote first, second;

    @BeforeEach void quotes() {
        first = quote(1L, "100", true);
        second = quote(2L, "150", false);
    }

    @Test void rejectsMissingTenantWithoutReadingAnything() {
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            assertThatThrownBy(() -> service.compare("deal", null)).isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Tenant");
            assertThatThrownBy(() -> service.markPrimary(1L)).isInstanceOf(BusinessException.class);
            verifyNoInteractions(repository);
        }
    }

    @Test void comparesAgainstPrimaryWithAccurateAmountsAndSkuDifferences() {
        first.setLineItems(List.of(line("A", 1), line("removed", 1)));
        second.setLineItems(List.of(line("A", 2), line("added", 1)));
        second.setTotalDiscount(new BigDecimal("15"));
        second.setTotalTax(new BigDecimal("13.50"));
        second.setSubtotal(new BigDecimal("150"));
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant");
            stubQuotes();
            var result = service.compare("deal", null);
            assertThat(result.baselineQuoteId()).isEqualTo(1L);
            var column = result.quotes().get(1);
            assertThat(column.discountPercent()).isEqualByComparingTo("10.00");
            assertThat(column.netAmount()).isEqualByComparingTo("135");
            assertThat(column.listTotal()).isEqualByComparingTo("60");
            assertThat(column.delta().grandTotal()).isEqualByComparingTo("50");
            assertThat(column.delta().grandTotalPercent()).isEqualByComparingTo("50.00");
            assertThat(column.skuDiff().added()).containsExactly("added");
            assertThat(column.skuDiff().removed()).containsExactly("removed");
            assertThat(column.skuDiff().changed()).containsExactly("A");
            assertThat(column.marginAmount()).isNull();
            assertThat(column.marginPercent()).isNull();
            assertThat(result.unavailableFields()).contains("marginAmount", "validUntil", "paymentTerms");
            verify(repository).findByTenantIdAndDealExternalIdAndDeletedFalseOrderByIdAsc("tenant", "deal");
        }
    }

    @Test void suppressesCrossCurrencyDeltas() {
        second.setCurrency("EUR");
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant"); stubQuotes();
            var delta = service.compare("deal", null).quotes().get(1).delta();
            assertThat(delta.comparableCurrency()).isFalse();
            assertThat(delta.grandTotal()).isNull();
            assertThat(delta.grandTotalPercent()).isNull();
        }
    }

    @Test void zeroDenominatorsAreUnknownAndDeletedLinesExcluded() {
        first.setSubtotal(BigDecimal.ZERO); first.setTotalAmount(BigDecimal.ZERO);
        var deleted = line("deleted", 10); deleted.setDeleted(true);
        first.setLineItems(List.of(deleted));
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant"); stubQuotes();
            var result = service.compare("deal", null).quotes().getFirst();
            assertThat(result.discountPercent()).isNull();
            assertThat(result.delta().grandTotalPercent()).isNull();
            assertThat(result.lineCount()).isZero();
            assertThat(result.listTotal()).isZero();
        }
    }

    @Test void filterUsesSelectedBaselineAndRejectsForeignQuoteIds() {
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant"); stubQuotes();
            var filtered = service.compare("deal", List.of(2L, 2L));
            assertThat(filtered.quotes()).hasSize(1);
            assertThat(filtered.baselineQuoteId()).isEqualTo(2L);
            assertThatThrownBy(() -> service.compare("deal", List.of(99L))).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test void emptyOpportunityAndMissingOpportunityAreHandled() {
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant");
            when(repository.findByTenantIdAndDealExternalIdAndDeletedFalseOrderByIdAsc("tenant", "deal")).thenReturn(List.of());
            assertThat(service.compare("deal", null).quotes()).isEmpty();
            assertThat(service.compare("deal", null).baselineQuoteId()).isNull();
            assertThatThrownBy(() -> service.compare(" ", null)).isInstanceOf(BusinessException.class);
        }
    }

    @Test void clearsPreviousPrimaryBeforeFlushingTargetAndIsIdempotent() {
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant"); stubQuotes();
            when(repository.findByIdAndTenantIdAndDeletedFalse(2L, "tenant")).thenReturn(Optional.of(second));
            Query lock = mock(Query.class);
            when(entityManager.createNativeQuery(anyString())).thenReturn(lock);
            when(lock.setParameter(eq("scope"), any())).thenReturn(lock);
            doAnswer(inv -> {
                assertThat(first.isPrimary()).isFalse();
                return null;
            }).when(repository).flush();
            when(mapper.toDto(second)).thenReturn(QuoteDto.builder().id(2L).primary(true).build());
            assertThat(service.markPrimary(2L).isPrimary()).isTrue();
            assertThat(second.isPrimary()).isTrue();
            verify(repository, times(2)).flush();
            service.markPrimary(2L);
            assertThat(second.isPrimary()).isTrue();
        }
    }

    @Test void foreignTenantTargetIsNotFoundAndUnlinkedQuoteRejected() {
        try (var tenant = mockStatic(QuoteTenantResolver.class)) {
            tenant.when(QuoteTenantResolver::currentTenantId).thenReturn("tenant");
            when(repository.findByIdAndTenantIdAndDeletedFalse(1L, "tenant")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.markPrimary(1L)).isInstanceOf(ResourceNotFoundException.class);
            first.setDealExternalId(null);
            when(repository.findByIdAndTenantIdAndDeletedFalse(1L, "tenant")).thenReturn(Optional.of(first));
            assertThatThrownBy(() -> service.markPrimary(1L)).isInstanceOf(BusinessException.class);
            verifyNoInteractions(entityManager);
        }
    }

    @Test void mapperIncludesPrimaryAndCreationNeverSetsIt() {
        QuoteMapper realMapper = new QuoteMapperImpl();
        assertThat(realMapper.toDto(first).isPrimary()).isTrue();
        assertThat(realMapper.toEntity(QuoteCreateRequest.builder().name("new").customerExternalId("customer").build()).isPrimary()).isFalse();
    }

    private void stubQuotes() {
        when(repository.findByTenantIdAndDealExternalIdAndDeletedFalseOrderByIdAsc("tenant", "deal")).thenReturn(List.of(first, second));
    }
    private static Quote quote(Long id, String total, boolean primary) {
        return Quote.builder().id(id).name("Quote " + id).quoteNumber("Q" + id).tenantId("tenant")
                .dealExternalId("deal").currency("USD").primary(primary)
                .subtotal(new BigDecimal(total)).totalAmount(new BigDecimal(total)).build();
    }
    private static QuoteLineItem line(String sku, int quantity) {
        return QuoteLineItem.builder().productExternalId(sku).quantity(quantity).listPrice(new BigDecimal("20"))
                .unitPrice(new BigDecimal("20")).billingFrequency("MONTHLY").build();
    }
}
