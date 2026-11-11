package com.opencrm.pricebook;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private CostBookService costBookService;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

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
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                eq(42L), eq("SKU-1"), eq("USD"), eq("EACH")))
                .thenReturn(List.of());
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
        assertThat(request.getUnitOfMeasure()).isEqualTo("EACH");
    }

    @Test
    void publishMarksPriceBookPublishedAndEmitsEvent() {
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .uuid("uuid-42")
                .currency("USD")
                .versionNumber(1)
                .status(PriceBookStatus.DRAFT)
                .build();
        PriceBookDto dto = PriceBookDto.builder().id(42L).status(PriceBookStatus.PUBLISHED).build();

        when(priceBookRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(priceBook));
        when(priceBookRepository.save(priceBook)).thenReturn(priceBook);
        when(priceBookMapper.toDto(priceBook)).thenReturn(dto);

        PriceBookDto result = priceBookService.publish(42L);

        assertThat(result).isSameAs(dto);
        assertThat(priceBook.getStatus()).isEqualTo(PriceBookStatus.PUBLISHED);
        assertThat(priceBook.getPublishedAt()).isNotNull();
        verify(outboxEventPublisher).publish(eq("PriceBook"), eq("uuid-42"),
                eq(EventContractRegistry.PRICE_BOOK_PUBLISHED_V1), any());
    }

    @Test
    void publishRejectsAlreadyPublishedPriceBook() {
        PriceBook priceBook = PriceBook.builder()
                .id(42L)
                .status(PriceBookStatus.PUBLISHED)
                .build();

        when(priceBookRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(priceBook));

        assertThatThrownBy(() -> priceBookService.publish(42L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already published");

        verify(outboxEventPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void getListPricePicksEntryEffectiveOnTheResolutionDate() {
        PriceBook priceBook = PriceBook.builder().id(42L).currency("USD").build();
        PriceBookEntry past = PriceBookEntry.builder()
                .listPrice(new BigDecimal("90.00"))
                .effectiveStartDate(LocalDate.of(2026, 1, 1))
                .effectiveEndDate(LocalDate.of(2026, 5, 31))
                .build();
        PriceBookEntry current = PriceBookEntry.builder()
                .listPrice(new BigDecimal("100.00"))
                .effectiveStartDate(LocalDate.of(2026, 6, 1))
                .effectiveEndDate(null)
                .build();

        when(priceBookRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(priceBook));
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                42L, "SKU-1", "USD", "EACH"))
                .thenReturn(List.of(past, current));

        PriceResolutionDto result = priceBookService.getListPrice(
                42L, "SKU-1", "USD", "EACH", LocalDate.of(2026, 7, 1));

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void getCostFollowsLinkedCostBook() {
        CostBook costBook = CostBook.builder().id(9L).build();
        PriceBook priceBook = PriceBook.builder().id(42L).costBook(costBook).build();
        PriceResolutionDto expected = PriceResolutionDto.builder().matched(true).amount(new BigDecimal("40.00")).build();

        when(priceBookRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(priceBook));
        when(costBookService.getCost(9L, "SKU-1", "USD", "EACH", null)).thenReturn(expected);

        PriceResolutionDto result = priceBookService.getCost(42L, "SKU-1", "USD", "EACH", null);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void getCostRejectsPriceBookWithNoLinkedCostBook() {
        PriceBook priceBook = PriceBook.builder().id(42L).build();
        when(priceBookRepository.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(priceBook));

        assertThatThrownBy(() -> priceBookService.getCost(42L, "SKU-1", "USD", "EACH", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("no linked cost book");
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
