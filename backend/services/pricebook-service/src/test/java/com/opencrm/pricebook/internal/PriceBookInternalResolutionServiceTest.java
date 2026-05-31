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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdInAndDeletedFalse(anyLong(), any()))
                .thenReturn(List.of(entry));

        ResolveBatchResponse response = service.resolveBatch("EMEA", List.of("SKU-1"));

        assertThat(response.getPriceBookId()).isEqualTo(42L);
        assertThat(response.getEntries().get("SKU-1").isEntryMatched()).isTrue();
        assertThat(response.getEntries().get("SKU-1").getListPrice()).isEqualByComparingTo("100.00");
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
