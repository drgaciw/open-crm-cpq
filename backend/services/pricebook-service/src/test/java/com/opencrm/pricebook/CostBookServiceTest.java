package com.opencrm.pricebook;

import com.opencrm.common.exception.BusinessException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CostBookServiceTest {

    @Mock
    private CostBookRepository costBookRepository;

    @Mock
    private CostBookEntryRepository costBookEntryRepository;

    @Mock
    private CostBookMapper costBookMapper;

    @InjectMocks
    private CostBookService costBookService;

    @Test
    void publishMarksCostBookPublished() {
        CostBook costBook = CostBook.builder().id(9L).status(PriceBookStatus.DRAFT).versionNumber(1).build();
        CostBookDto dto = CostBookDto.builder().id(9L).status(PriceBookStatus.PUBLISHED).build();

        when(costBookRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(costBook));
        when(costBookRepository.save(costBook)).thenReturn(costBook);
        when(costBookMapper.toDto(costBook)).thenReturn(dto);

        CostBookDto result = costBookService.publish(9L);

        assertThat(result).isSameAs(dto);
        assertThat(costBook.getStatus()).isEqualTo(PriceBookStatus.PUBLISHED);
        assertThat(costBook.getPublishedAt()).isNotNull();
    }

    @Test
    void publishRejectsAlreadyPublishedCostBook() {
        CostBook costBook = CostBook.builder().id(9L).status(PriceBookStatus.PUBLISHED).build();
        when(costBookRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(costBook));

        assertThatThrownBy(() -> costBookService.publish(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already published");
    }

    @Test
    void createEntryRejectsOverlappingEffectiveDateRange() {
        CostBook costBook = CostBook.builder().id(9L).currency("USD").build();
        CostBookEntry existing = CostBookEntry.builder()
                .id(1L)
                .currency("USD")
                .unitOfMeasure("EACH")
                .effectiveStartDate(LocalDate.of(2026, 1, 1))
                .effectiveEndDate(LocalDate.of(2026, 6, 30))
                .build();
        CostBookEntryCreateRequest request = CostBookEntryCreateRequest.builder()
                .sku("SKU-1")
                .cost(new BigDecimal("30.00"))
                .effectiveStartDate(LocalDate.of(2026, 4, 1))
                .build();

        when(costBookRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(costBook));
        when(costBookEntryRepository.findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                eq(9L), eq("SKU-1"), eq("USD"), eq("EACH")))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> costBookService.createEntry(9L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("overlapping effective date range")
                .hasFieldOrPropertyWithValue("errorCode", "OVERLAPPING_COST_BOOK_ENTRY_RANGE");

        verify(costBookEntryRepository, never()).save(any());
    }

    @Test
    void createEntryAllowsAdjacentNonOverlappingEffectiveDateRange() {
        CostBook costBook = CostBook.builder().id(9L).currency("USD").build();
        CostBookEntry existing = CostBookEntry.builder()
                .id(1L)
                .currency("USD")
                .unitOfMeasure("EACH")
                .effectiveStartDate(LocalDate.of(2026, 1, 1))
                .effectiveEndDate(LocalDate.of(2026, 6, 30))
                .build();
        CostBookEntry mappedEntry = CostBookEntry.builder().build();
        CostBookEntryDto savedDto = CostBookEntryDto.builder().costBookId(9L).build();
        CostBookEntryCreateRequest request = CostBookEntryCreateRequest.builder()
                .sku("SKU-1")
                .cost(new BigDecimal("30.00"))
                .effectiveStartDate(LocalDate.of(2026, 7, 1))
                .build();

        when(costBookRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(costBook));
        when(costBookEntryRepository.findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                eq(9L), eq("SKU-1"), eq("USD"), eq("EACH")))
                .thenReturn(List.of(existing));
        when(costBookMapper.toEntryEntity(request)).thenReturn(mappedEntry);
        when(costBookEntryRepository.save(mappedEntry)).thenReturn(mappedEntry);
        when(costBookMapper.toEntryDto(mappedEntry)).thenReturn(savedDto);

        CostBookEntryDto result = costBookService.createEntry(9L, request);

        assertThat(result).isSameAs(savedDto);
        verify(costBookEntryRepository).save(mappedEntry);
    }

    @Test
    void updateEntryExcludesItselfFromTheOverlapCheck() {
        CostBook costBook = CostBook.builder().id(9L).currency("USD").build();
        CostBookEntry entryToUpdate = CostBookEntry.builder()
                .id(1L)
                .sku("SKU-1")
                .currency("USD")
                .unitOfMeasure("EACH")
                .cost(new BigDecimal("30.00"))
                .effectiveStartDate(LocalDate.of(2026, 1, 1))
                .effectiveEndDate(LocalDate.of(2026, 6, 30))
                .build();
        // Only widening its own end date by a day; must not be rejected as "overlapping itself".
        CostBookEntryUpdateRequest request = CostBookEntryUpdateRequest.builder()
                .effectiveEndDate(LocalDate.of(2026, 7, 1))
                .build();

        when(costBookRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(costBook));
        when(costBookEntryRepository.findByIdAndCostBookIdAndDeletedFalse(1L, 9L))
                .thenReturn(Optional.of(entryToUpdate));
        when(costBookEntryRepository.findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                eq(9L), eq("SKU-1"), eq("USD"), eq("EACH")))
                .thenReturn(List.of(entryToUpdate));
        when(costBookEntryRepository.save(entryToUpdate)).thenReturn(entryToUpdate);
        when(costBookMapper.toEntryDto(entryToUpdate)).thenReturn(CostBookEntryDto.builder().build());

        costBookService.updateEntry(9L, 1L, request);

        verify(costBookEntryRepository).save(entryToUpdate);
    }

    @Test
    void getCostPicksEntryEffectiveOnTheResolutionDateAndPrefersMostRecentStart() {
        // Simulates data written before overlap validation existed (or outside it): CostBookService's own
        // create/update path would now reject two open-ended, overlapping entries for the same key.
        CostBook costBook = CostBook.builder().id(9L).build();
        CostBookEntry older = CostBookEntry.builder()
                .cost(new BigDecimal("30.00"))
                .effectiveStartDate(LocalDate.of(2026, 1, 1))
                .effectiveEndDate(null)
                .build();
        CostBookEntry newer = CostBookEntry.builder()
                .cost(new BigDecimal("35.00"))
                .effectiveStartDate(LocalDate.of(2026, 6, 1))
                .effectiveEndDate(null)
                .build();

        when(costBookRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(costBook));
        when(costBookEntryRepository.findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                9L, "SKU-1", "USD", "EACH"))
                .thenReturn(List.of(older, newer));

        PriceResolutionDto result = costBookService.getCost(9L, "SKU-1", "USD", "EACH", LocalDate.of(2026, 7, 1));

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getAmount()).isEqualByComparingTo("35.00");
    }

    @Test
    void getCostReportsUnmatchedWhenNoCandidateIsEffective() {
        CostBook costBook = CostBook.builder().id(9L).build();
        CostBookEntry future = CostBookEntry.builder()
                .cost(new BigDecimal("30.00"))
                .effectiveStartDate(LocalDate.of(2027, 1, 1))
                .build();

        when(costBookRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(costBook));
        when(costBookEntryRepository.findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                9L, "SKU-1", "USD", "EACH"))
                .thenReturn(List.of(future));

        PriceResolutionDto result = costBookService.getCost(9L, "SKU-1", "USD", "EACH", LocalDate.of(2026, 7, 1));

        assertThat(result.isMatched()).isFalse();
        assertThat(result.getAmount()).isNull();
    }
}
