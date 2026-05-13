package com.opencrm.pricebook;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceBookServiceTest {

    @Mock
    private PriceBookRepository priceBookRepository;

    @Mock
    private PriceBookEntryRepository priceBookEntryRepository;

    @Mock
    private PriceBookMapper priceBookMapper;

    @InjectMocks
    private PriceBookService priceBookService;

    @Test
    void createEntryAttachesEntryToPriceBookAggregate() {
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .currency("USD")
                .effectiveDate(LocalDate.now().plusDays(1))
                .build();
        PriceBookEntry mappedEntry = PriceBookEntry.builder().build();
        PriceBookEntryDto savedDto = PriceBookEntryDto.builder().priceBookId(42L).build();
        PriceBookEntryCreateRequest request = PriceBookEntryCreateRequest.builder()
                .productExternalId("SKU-1")
                .listPrice(new BigDecimal("100.00"))
                .floorPrice(new BigDecimal("80.00"))
                .build();

        when(priceBookRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(priceBook));
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndDeletedFalse(42L, "SKU-1"))
                .thenReturn(Optional.empty());
        when(priceBookMapper.toEntryEntity(request)).thenReturn(mappedEntry);
        when(priceBookEntryRepository.save(mappedEntry)).thenReturn(mappedEntry);
        when(priceBookMapper.toEntryDto(mappedEntry)).thenReturn(savedDto);

        PriceBookEntryDto result = priceBookService.createEntry(42L, request);

        assertThat(result).isSameAs(savedDto);
        ArgumentCaptor<PriceBookEntry> entryCaptor = ArgumentCaptor.forClass(PriceBookEntry.class);
        verify(priceBookEntryRepository).save(entryCaptor.capture());
        assertThat(entryCaptor.getValue().getPriceBook()).isSameAs(priceBook);
        assertThat(entryCaptor.getValue().getPriceBookId()).isEqualTo(42L);
        assertThat(request.getCurrency()).isEqualTo("USD");
    }

    @Test
    void updateEntryRejectsPublishedPriceBook() {
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .effectiveDate(LocalDate.now())
                .build();
        PriceBookEntryUpdateRequest request = PriceBookEntryUpdateRequest.builder()
                .listPrice(new BigDecimal("110.00"))
                .build();

        when(priceBookRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(priceBook));

        assertThatThrownBy(() -> priceBookService.updateEntry(42L, 7L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Published price books and their entries are immutable");

        verify(priceBookEntryRepository, never()).save(any());
    }

    @Test
    void createRejectsExpirationBeforeEffectiveDate() {
        PriceBookCreateRequest request = PriceBookCreateRequest.builder()
                .name("Future EMEA")
                .region("EMEA")
                .effectiveDate(LocalDate.now().plusDays(10))
                .expirationDate(LocalDate.now().plusDays(9))
                .build();

        assertThatThrownBy(() -> priceBookService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Expiration date must be on or after effective date");

        verify(priceBookRepository, never()).save(any());
    }
}
