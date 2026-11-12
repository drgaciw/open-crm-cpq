package com.opencrm.pricebook.internal;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricebook.PriceBook;
import com.opencrm.pricebook.PriceBookEntry;
import com.opencrm.pricebook.PriceBookEntryRepository;
import com.opencrm.pricebook.PriceBookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceBookInternalResolutionServiceTest {

    @Mock
    private PriceBookRepository priceBookRepository;

    @Mock
    private PriceBookEntryRepository priceBookEntryRepository;

    @InjectMocks
    private PriceBookInternalResolutionService service;

    @Test
    void resolveBatchUsesOnlyEffectivePriceBook() {
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .region("EMEA")
                .currency("EUR")
                .effectiveDate(LocalDate.now().minusDays(1))
                .expirationDate(LocalDate.now().plusDays(1))
                .build();
        PriceBookEntry entry = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("100.00"))
                .floorPrice(new BigDecimal("80.00"))
                .currency("EUR")
                .build();

        when(priceBookRepository.findByRegionAndDeletedFalse("EMEA")).thenReturn(Optional.of(priceBook));
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdInAndDeletedFalse(
                eq(42L), eq(List.of("SKU-1"))))
                .thenReturn(List.of(entry));

        ResolveBatchResponse response = service.resolveBatch("EMEA", List.of("SKU-1"));

        assertThat(response.getPriceBookId()).isEqualTo(42L);
        assertThat(response.getEntries().get("SKU-1").isEntryMatched()).isTrue();
        assertThat(response.getEntries().get("SKU-1").getListPrice()).isEqualByComparingTo("100.00");
        verify(priceBookEntryRepository, times(1))
                .findByPriceBookIdAndProductExternalIdInAndDeletedFalse(eq(42L), anyCollection());
        verify(priceBookEntryRepository, never())
                .findByPriceBookIdAndProductExternalIdAndDeletedFalse(anyLong(), anyString());
    }

    @Test
    void resolveBatchFetchesEntriesInSingleQueryForMultipleProducts() {
        PriceBook priceBook = PriceBook.builder()
                .id(7L)
                .region("NA")
                .currency("USD")
                .isDefault(true)
                .effectiveDate(LocalDate.now().minusDays(1))
                .build();
        PriceBookEntry matched = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("PROD-A")
                .listPrice(new BigDecimal("50.00"))
                .floorPrice(new BigDecimal("40.00"))
                .currency("USD")
                .build();
        List<String> productIds = List.of("PROD-A", "PROD-MISSING", "PROD-B");
        PriceBookEntry matchedB = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("PROD-B")
                .listPrice(new BigDecimal("75.00"))
                .floorPrice(new BigDecimal("60.00"))
                .currency("USD")
                .build();

        when(priceBookRepository.findByRegionAndDeletedFalse("NA")).thenReturn(Optional.of(priceBook));
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdInAndDeletedFalse(
                eq(7L), eq(productIds)))
                .thenReturn(List.of(matched, matchedB));

        ResolveBatchResponse response = service.resolveBatch("NA", productIds);

        assertThat(response.getEntries()).hasSize(3);
        assertThat(response.getEntries().get("PROD-A").isEntryMatched()).isTrue();
        assertThat(response.getEntries().get("PROD-A").getListPrice()).isEqualByComparingTo("50.00");
        assertThat(response.getEntries().get("PROD-MISSING").isEntryMatched()).isFalse();
        assertThat(response.getEntries().get("PROD-MISSING").getCurrency()).isEqualTo("USD");
        assertThat(response.getEntries().get("PROD-B").isEntryMatched()).isTrue();
        assertThat(response.getEntries().get("PROD-B").getListPrice()).isEqualByComparingTo("75.00");

        // Proves batching: one IN query for N products, never the per-id finder.
        verify(priceBookEntryRepository, times(1))
                .findByPriceBookIdAndProductExternalIdInAndDeletedFalse(eq(7L), anyCollection());
        verify(priceBookEntryRepository, never())
                .findByPriceBookIdAndProductExternalIdAndDeletedFalse(anyLong(), anyString());
    }

    @Test
    void resolveBatchRejectsFutureDatedPriceBook() {
        PriceBook futureRegionalBook = PriceBook.builder()
                .id(42L)
                .region("EMEA")
                .currency("EUR")
                .effectiveDate(LocalDate.now().plusDays(1))
                .build();

        when(priceBookRepository.findByRegionAndDeletedFalse("EMEA")).thenReturn(Optional.of(futureRegionalBook));
        when(priceBookRepository.findByIsDefaultTrueAndDeletedFalse()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveBatch("EMEA", List.of("SKU-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No price book found");
    }

    @Test
    void resolveBatchPicksTheDateEffectiveEntryWhenASkuHasSeveralDatedVersions() {
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .region("EMEA")
                .currency("EUR")
                .effectiveDate(LocalDate.now().minusDays(1))
                .build();
        PriceBookEntry past = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("90.00"))
                .currency("EUR")
                .effectiveStartDate(LocalDate.now().minusMonths(6))
                .effectiveEndDate(LocalDate.now().minusDays(1))
                .build();
        PriceBookEntry current = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("100.00"))
                .currency("EUR")
                .effectiveStartDate(LocalDate.now())
                .effectiveEndDate(null)
                .build();

        when(priceBookRepository.findByRegionAndDeletedFalse("EMEA")).thenReturn(Optional.of(priceBook));
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdInAndDeletedFalse(
                eq(42L), eq(List.of("SKU-1"))))
                .thenReturn(List.of(past, current));

        ResolveBatchResponse response = service.resolveBatch("EMEA", List.of("SKU-1"));

        assertThat(response.getEntries().get("SKU-1").isEntryMatched()).isTrue();
        assertThat(response.getEntries().get("SKU-1").getListPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void resolveBatchIgnoresAnEffectiveCaseEntryAndResolvesTheEachEntryForTheSameSku() {
        // Codex P1: the V11 exclusion constraint keys on (price_book_id, sku, currency, unit_of_measure),
        // so an EACH entry and a CASE entry for the same SKU are explicitly allowed to coexist and both be
        // effective today. resolveBatch has no caller-supplied unit, so it must resolve the default (EACH)
        // entry, not whichever started most recently regardless of unit — the CASE entry below starts
        // later, so a regression here would make this test fail by returning the CASE price (250.00).
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .region("EMEA")
                .currency("EUR")
                .effectiveDate(LocalDate.now().minusDays(1))
                .build();
        PriceBookEntry eachEntry = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("100.00"))
                .currency("EUR")
                .unitOfMeasure("EACH")
                .effectiveStartDate(LocalDate.now().minusMonths(1))
                .build();
        PriceBookEntry caseEntry = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("250.00"))
                .currency("EUR")
                .unitOfMeasure("CASE")
                .effectiveStartDate(LocalDate.now()) // later start than eachEntry
                .build();

        when(priceBookRepository.findByRegionAndDeletedFalse("EMEA")).thenReturn(Optional.of(priceBook));
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdInAndDeletedFalse(
                eq(42L), eq(List.of("SKU-1"))))
                .thenReturn(List.of(eachEntry, caseEntry));

        ResolveBatchResponse response = service.resolveBatch("EMEA", List.of("SKU-1"));

        assertThat(response.getEntries().get("SKU-1").isEntryMatched()).isTrue();
        assertThat(response.getEntries().get("SKU-1").getListPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void resolveBatchIgnoresAnEffectiveEntryInAnotherCurrencyForTheSameSku() {
        // Same key shape, but currency differs instead of unit of measure — the book's own currency wins.
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .region("EMEA")
                .currency("EUR")
                .effectiveDate(LocalDate.now().minusDays(1))
                .build();
        PriceBookEntry eurEntry = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("100.00"))
                .currency("EUR")
                .unitOfMeasure("EACH")
                .effectiveStartDate(LocalDate.now().minusMonths(1))
                .build();
        PriceBookEntry usdEntry = PriceBookEntry.builder()
                .priceBook(priceBook)
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("120.00"))
                .currency("USD")
                .unitOfMeasure("EACH")
                .effectiveStartDate(LocalDate.now()) // later start than eurEntry
                .build();

        when(priceBookRepository.findByRegionAndDeletedFalse("EMEA")).thenReturn(Optional.of(priceBook));
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdInAndDeletedFalse(
                eq(42L), eq(List.of("SKU-1"))))
                .thenReturn(List.of(eurEntry, usdEntry));

        ResolveBatchResponse response = service.resolveBatch("EMEA", List.of("SKU-1"));

        assertThat(response.getEntries().get("SKU-1").isEntryMatched()).isTrue();
        assertThat(response.getEntries().get("SKU-1").getListPrice()).isEqualByComparingTo("100.00");
    }

    @Test
    void resolveBatchRejectsExpiredDefaultPriceBook() {
        PriceBook expiredDefault = PriceBook.builder()
                .id(1L)
                .isDefault(true)
                .effectiveDate(LocalDate.now().minusDays(10))
                .expirationDate(LocalDate.now().minusDays(1))
                .build();

        when(priceBookRepository.findByIsDefaultTrueAndDeletedFalse()).thenReturn(Optional.of(expiredDefault));

        assertThatThrownBy(() -> service.resolveBatch(null, List.of("SKU-1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No price book found");
    }
}
