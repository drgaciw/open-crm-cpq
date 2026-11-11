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
    void getCostPicksEntryEffectiveOnTheResolutionDateAndPrefersMostRecentStart() {
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
