package com.opencrm.pricebook.internal;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.pricebook.PriceBook;
import com.opencrm.pricebook.PriceBookEntry;
import com.opencrm.pricebook.PriceBookEntryRepository;
import com.opencrm.pricebook.PriceBookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceBookInternalResolutionService {

    private final PriceBookRepository priceBookRepository;
    private final PriceBookEntryRepository priceBookEntryRepository;

    @Transactional(readOnly = true)
    public ResolveBatchResponse resolveBatch(String region, List<String> productExternalIds) {
        PriceBook priceBook = findPriceBook(region);
        Map<String, ProductEntryResolveDto> entries = new LinkedHashMap<>();
        if (productExternalIds == null || productExternalIds.isEmpty()) {
            return ResolveBatchResponse.builder()
                    .priceBookId(priceBook.getId())
                    .priceBookCurrency(priceBook.getCurrency())
                    .entries(entries)
                    .build();
        }
        // Single IN query — avoids N+1 per productExternalId (GitHub #456 family). A product may now have
        // several date-ranged entries (DRG-431); group them and pick, per product, the one effective today.
        LocalDate today = LocalDate.now();
        Map<String, PriceBookEntry> entryByProductId = new LinkedHashMap<>();
        priceBookEntryRepository
                .findByPriceBookIdAndProductExternalIdInAndDeletedFalse(priceBook.getId(), productExternalIds)
                .stream()
                .collect(Collectors.groupingBy(PriceBookEntry::getProductExternalId))
                .forEach((productExternalId, candidates) -> {
                    PriceBookEntry best = bestEffectiveEntry(candidates, today);
                    if (best != null) {
                        entryByProductId.put(productExternalId, best);
                    }
                });
        for (String productExternalId : productExternalIds) {
            PriceBookEntry e = entryByProductId.get(productExternalId);
            if (e != null) {
                entries.put(productExternalId, ProductEntryResolveDto.builder()
                        .entryMatched(true)
                        .listPrice(e.getListPrice())
                        .floorPrice(e.getFloorPrice())
                        .currency(e.getCurrency())
                        .build());
            } else {
                entries.put(productExternalId, ProductEntryResolveDto.builder()
                        .entryMatched(false)
                        .currency(priceBook.getCurrency())
                        .build());
            }
        }
        return ResolveBatchResponse.builder()
                .priceBookId(priceBook.getId())
                .priceBookCurrency(priceBook.getCurrency())
                .entries(entries)
                .build();
    }

    /**
     * DRG-431: among a SKU's candidate entries, the one whose EffectiveDateRange covers {@code asOfDate}.
     * {@code PriceBookService} rejects overlapping ranges for the same Sku+currency+UnitOfMeasure at write
     * time, so at most one candidate should ever match; "most recently started" is a defensive pick for
     * rows that predate that validation or were written outside it, not a tie-break over data this service
     * expects to see.
     */
    private PriceBookEntry bestEffectiveEntry(List<PriceBookEntry> candidates, LocalDate asOfDate) {
        return candidates.stream()
                .filter(entry -> entry.isEffectiveOn(asOfDate))
                .max(Comparator.comparing(PriceBookEntry::getEffectiveStartDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private PriceBook findPriceBook(String region) {
        if (region != null) {
            Optional<PriceBook> regionalBook = priceBookRepository.findByRegionAndDeletedFalse(region);
            if (regionalBook.filter(this::isEffective).isPresent()) {
                return regionalBook.get();
            }
        }
        return priceBookRepository.findByIsDefaultTrueAndDeletedFalse()
                .filter(this::isEffective)
                .orElseThrow(() -> new BusinessException("No price book found", "NO_PRICE_BOOK"));
    }

    private boolean isEffective(PriceBook priceBook) {
        LocalDate today = LocalDate.now();
        LocalDate effectiveDate = priceBook.getEffectiveDate();
        LocalDate expirationDate = priceBook.getExpirationDate();
        return (effectiveDate == null || !effectiveDate.isAfter(today))
                && (expirationDate == null || !expirationDate.isBefore(today));
    }
}
