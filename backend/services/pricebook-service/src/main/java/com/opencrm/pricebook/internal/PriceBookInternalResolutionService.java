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

    /**
     * DRG-431 PR review follow-up (Codex P1). {@code resolveBatch}'s request/response contract carries
     * neither a currency nor a unit of measure per product — confirmed by checking its only real caller,
     * {@code pricing-service}'s {@code PricingExecutionService} via {@code RestPriceBookResolutionClient}:
     * {@code PricingLineRequest} has no currency or unit-of-measure field at all today, so no caller can
     * ask for a specific one. Entries default to this unit unless explicitly overridden
     * ({@code PriceBookService#normalizeEntryCreateRequest}, and the V10 migration backfilled it onto
     * every pre-existing row), so it is what every current caller implicitly means.
     */
    private static final String DEFAULT_UNIT_OF_MEASURE = "EACH";

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
        // several date-ranged entries (DRG-431); group them and pick, per product, the one effective today
        // in the book's own currency and the default unit of measure (see DEFAULT_UNIT_OF_MEASURE) — other
        // currency/UOM variants for the same SKU are deliberately ignored here, not compared against it.
        LocalDate today = LocalDate.now();
        String defaultCurrency = priceBook.getCurrency();
        Map<String, PriceBookEntry> entryByProductId = new LinkedHashMap<>();
        priceBookEntryRepository
                .findByPriceBookIdAndProductExternalIdInAndDeletedFalse(priceBook.getId(), productExternalIds)
                .stream()
                .collect(Collectors.groupingBy(PriceBookEntry::getProductExternalId))
                .forEach((productExternalId, candidates) -> {
                    PriceBookEntry best = bestEffectiveEntry(candidates, today, defaultCurrency);
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
     * DRG-431 / Codex P1 follow-up: among a SKU's candidate entries, the one in {@code defaultCurrency}
     * and {@link #DEFAULT_UNIT_OF_MEASURE} whose EffectiveDateRange covers {@code asOfDate}.
     *
     * <p>A SKU can legitimately have other entries this book's write path does not reject — e.g. an EACH
     * and a CASE entry both effective today — because the V11 exclusion constraint keys on
     * {@code (price_book_id, product_external_id, currency, unit_of_measure)}: two entries differing in
     * currency or unit of measure do not overlap under that constraint, by design, and are expected to
     * coexist. Filtering to one specific currency and unit of measure first is what makes "at most one
     * candidate should ever match" true within that filtered set; without it, this method would compare
     * entries across different units by start date alone, which is not a meaningful comparison (a method
     * this review caught doing exactly that).
     */
    private PriceBookEntry bestEffectiveEntry(List<PriceBookEntry> candidates, LocalDate asOfDate,
                                               String defaultCurrency) {
        return candidates.stream()
                .filter(entry -> DEFAULT_UNIT_OF_MEASURE.equals(entry.getUnitOfMeasure()))
                .filter(entry -> defaultCurrency == null || defaultCurrency.equals(entry.getCurrency()))
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
