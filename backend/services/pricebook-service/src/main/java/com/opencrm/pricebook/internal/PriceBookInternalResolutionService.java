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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        // ⚡ Bolt Performance Optimization:
        // Replaced O(N) database queries inside the loop with an O(1) batch query using an IN clause.
        // Expected impact: Drastically reduces database round-trip latency when resolving large batches of product IDs.
        List<PriceBookEntry> fetchedEntries = priceBookEntryRepository
                .findByPriceBookIdAndProductExternalIdInAndDeletedFalse(priceBook.getId(), productExternalIds);

        // Use a Map for O(1) in-memory lookup
        Map<String, PriceBookEntry> entryMap = fetchedEntries.stream()
                .collect(java.util.stream.Collectors.toMap(PriceBookEntry::getProductExternalId, e -> e));

        for (String productExternalId : productExternalIds) {
            PriceBookEntry e = entryMap.get(productExternalId);
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
