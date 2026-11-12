package com.opencrm.pricebook;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.PriceBookPublishedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PriceBookService {

    private final PriceBookRepository priceBookRepository;
    private final PriceBookEntryRepository priceBookEntryRepository;
    private final PriceBookMapper priceBookMapper;
    private final CostBookService costBookService;
    private final OutboxEventPublisher outboxEventPublisher;

    public PriceBookDto create(PriceBookCreateRequest request) {
        normalizeCreateRequest(request);
        validateDateRange(request.getEffectiveDate(), request.getExpirationDate());
        validateRegionUniqueness(request.getRegion(), null);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearCurrentDefault(null);
        }

        PriceBook priceBook = priceBookMapper.toEntity(request);
        if (request.getCostBookId() != null) {
            priceBook.setCostBook(costBookService.requireCostBook(request.getCostBookId()));
        }
        PriceBook saved = priceBookRepository.save(priceBook);
        log.info("Created price book with ID: {}", saved.getId());
        return priceBookMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PriceBookDto> list(Pageable pageable, String region) {
        String normalizedRegion = normalize(region);
        Page<PriceBook> page = normalizedRegion == null
                ? priceBookRepository.findByDeletedFalse(pageable)
                : priceBookRepository.findByRegionAndDeletedFalse(normalizedRegion, pageable);

        return page.map(priceBookMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PriceBookDto getById(Long id) {
        return priceBookMapper.toDto(requirePriceBook(id));
    }

    public PriceBookDto update(Long id, PriceBookUpdateRequest request) {
        PriceBook existing = requirePriceBookForUpdate(id);
        ensureNotPublished(existing);
        normalizeUpdateRequest(request);

        String targetRegion = request.getRegion() != null ? request.getRegion() : existing.getRegion();
        validateRegionUniqueness(targetRegion, id);

        LocalDate targetEffectiveDate =
                request.getEffectiveDate() != null ? request.getEffectiveDate() : existing.getEffectiveDate();
        LocalDate targetExpirationDate =
                request.getExpirationDate() != null ? request.getExpirationDate() : existing.getExpirationDate();
        validateDateRange(targetEffectiveDate, targetExpirationDate);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearCurrentDefault(id);
        }

        priceBookMapper.updateEntity(request, existing);
        if (request.getCostBookId() != null) {
            existing.setCostBook(costBookService.requireCostBook(request.getCostBookId()));
        }
        PriceBook updated = priceBookRepository.save(existing);
        log.info("Updated price book with ID: {}", updated.getId());
        return priceBookMapper.toDto(updated);
    }

    /**
     * PublishPriceBookVersion: makes this price book version immutable, stamps {@code publishedAt}, and emits
     * PriceBookPublished (AC: "PriceBookPublished event is emitted"). The cost book it links to, if any, is
     * published separately via {@link CostBookService#publish(Long)} — list prices and costs stay versioned
     * and publishable independently.
     */
    public PriceBookDto publish(Long id) {
        PriceBook priceBook = requirePriceBookForUpdate(id);
        if (priceBook.isPublished()) {
            throw new BusinessException("Price book is already published", "PRICE_BOOK_ALREADY_PUBLISHED");
        }
        priceBook.setStatus(PriceBookStatus.PUBLISHED);
        priceBook.setPublishedAt(Instant.now());
        PriceBook saved = priceBookRepository.save(priceBook);

        outboxEventPublisher.publish("PriceBook", saved.getUuid(), EventContractRegistry.PRICE_BOOK_PUBLISHED_V1,
                new PriceBookPublishedEvent(
                        saved.getId(),
                        saved.getUuid(),
                        saved.getVersionNumber(),
                        saved.getCurrency(),
                        saved.getRegion(),
                        saved.getCostBookId(),
                        saved.getEffectiveDate(),
                        saved.getExpirationDate(),
                        saved.getPublishedAt()));

        log.info("Published price book with ID: {} (version {})", saved.getId(), saved.getVersionNumber());
        return priceBookMapper.toDto(saved);
    }

    /**
     * GetListPrice: resolves the list price for a SKU, currency and unit of measure as of a date, among this
     * price book's (possibly several, date-ranged) entries for that key. Entry authoring rejects
     * overlapping ranges for the same key ({@link #validateNoOverlappingEntry}), so at most one candidate
     * can cover any given date; the "most recently started" pick below is then provably correct, not a
     * tie-break over ambiguous data — it only matters for rows written before this validation existed or
     * outside it (direct SQL, etc.).
     */
    @Transactional(readOnly = true)
    public PriceResolutionDto getListPrice(Long priceBookId, String sku, String currency, String unitOfMeasure,
                                            LocalDate asOfDate) {
        requirePriceBook(priceBookId);
        LocalDate resolutionDate = asOfDate != null ? asOfDate : LocalDate.now();
        String normalizedUom = unitOfMeasure != null ? unitOfMeasure : "EACH";

        List<PriceBookEntry> candidates = priceBookEntryRepository
                .findByPriceBookIdAndProductExternalIdAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                        priceBookId, sku, currency, normalizedUom);

        return candidates.stream()
                .filter(entry -> entry.isEffectiveOn(resolutionDate))
                .max(Comparator.comparing(PriceBookEntry::getEffectiveStartDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(entry -> PriceResolutionDto.builder()
                        .bookId(priceBookId)
                        .sku(sku)
                        .currency(currency)
                        .unitOfMeasure(normalizedUom)
                        .asOfDate(resolutionDate)
                        .matched(true)
                        .amount(entry.getListPrice())
                        .build())
                .orElseGet(() -> PriceResolutionDto.builder()
                        .bookId(priceBookId)
                        .sku(sku)
                        .currency(currency)
                        .unitOfMeasure(normalizedUom)
                        .asOfDate(resolutionDate)
                        .matched(false)
                        .build());
    }

    /**
     * GetCost: follows this price book's cost book linkage and resolves the cost there, as of a date.
     */
    @Transactional(readOnly = true)
    public PriceResolutionDto getCost(Long priceBookId, String sku, String currency, String unitOfMeasure,
                                       LocalDate asOfDate) {
        PriceBook priceBook = requirePriceBook(priceBookId);
        if (priceBook.getCostBookId() == null) {
            throw new BusinessException(
                    "Price book " + priceBookId + " has no linked cost book", "NO_LINKED_COST_BOOK");
        }
        return costBookService.getCost(priceBook.getCostBookId(), sku, currency, unitOfMeasure, asOfDate);
    }

    public void delete(Long id) {
        PriceBook existing = requirePriceBookForUpdate(id);
        ensureNotPublished(existing);
        existing.softDelete();
        priceBookRepository.save(existing);
        log.info("Deleted price book with ID: {}", id);
    }

    public PriceBookEntryDto createEntry(Long priceBookId, PriceBookEntryCreateRequest request) {
        PriceBook priceBook = requirePriceBookForUpdate(priceBookId);
        ensureNotPublished(priceBook);
        normalizeEntryCreateRequest(request, priceBook);
        validateEntryPriceRange(request.getListPrice(), request.getFloorPrice());
        validateNoOverlappingEntry(priceBookId, request.getProductExternalId(), request.getCurrency(),
                request.getUnitOfMeasure(), request.getEffectiveStartDate(), request.getEffectiveEndDate(), null);

        PriceBookEntry entry = priceBookMapper.toEntryEntity(request);
        entry.setPriceBook(priceBook);
        PriceBookEntry saved = priceBookEntryRepository.save(entry);
        log.info("Created price book entry with ID: {} for price book ID: {}", saved.getId(), priceBookId);
        return priceBookMapper.toEntryDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PriceBookEntryDto> listEntries(Long priceBookId, Pageable pageable) {
        requirePriceBook(priceBookId);
        return priceBookEntryRepository.findByPriceBookIdAndDeletedFalse(priceBookId, pageable)
                .map(priceBookMapper::toEntryDto);
    }

    @Transactional(readOnly = true)
    public PriceBookEntryDto getEntry(Long priceBookId, Long entryId) {
        requirePriceBook(priceBookId);
        PriceBookEntry entry = requireEntry(priceBookId, entryId);
        return priceBookMapper.toEntryDto(entry);
    }

    public PriceBookEntryDto updateEntry(Long priceBookId, Long entryId, PriceBookEntryUpdateRequest request) {
        PriceBook priceBook = requirePriceBookForUpdate(priceBookId);
        ensureNotPublished(priceBook);
        PriceBookEntry entry = requireEntry(priceBookId, entryId);
        normalizeEntryUpdateRequest(request);

        String targetProductExternalId = request.getProductExternalId() != null
                ? request.getProductExternalId() : entry.getProductExternalId();
        String targetCurrency = request.getCurrency() != null ? request.getCurrency() : entry.getCurrency();
        String targetUom = request.getUnitOfMeasure() != null ? request.getUnitOfMeasure() : entry.getUnitOfMeasure();
        LocalDate targetStart = request.getEffectiveStartDate() != null
                ? request.getEffectiveStartDate() : entry.getEffectiveStartDate();
        LocalDate targetEnd = request.getEffectiveEndDate() != null
                ? request.getEffectiveEndDate() : entry.getEffectiveEndDate();
        validateNoOverlappingEntry(priceBookId, targetProductExternalId, targetCurrency, targetUom,
                targetStart, targetEnd, entryId);

        BigDecimal targetListPrice = request.getListPrice() != null ? request.getListPrice() : entry.getListPrice();
        BigDecimal targetFloorPrice = request.getFloorPrice() != null ? request.getFloorPrice() : entry.getFloorPrice();
        validateEntryPriceRange(targetListPrice, targetFloorPrice);

        priceBookMapper.updateEntryEntity(request, entry);
        PriceBookEntry updated = priceBookEntryRepository.save(entry);
        log.info("Updated price book entry with ID: {} for price book ID: {}", entryId, priceBookId);
        return priceBookMapper.toEntryDto(updated);
    }

    public void deleteEntry(Long priceBookId, Long entryId) {
        PriceBook priceBook = requirePriceBookForUpdate(priceBookId);
        ensureNotPublished(priceBook);
        PriceBookEntry entry = requireEntry(priceBookId, entryId);
        entry.softDelete();
        priceBookEntryRepository.save(entry);
        log.info("Deleted price book entry with ID: {} for price book ID: {}", entryId, priceBookId);
    }

    private PriceBook requirePriceBook(Long id) {
        return priceBookRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("PriceBook", id));
    }

    private PriceBook requirePriceBookForUpdate(Long id) {
        return priceBookRepository.findWithLockByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("PriceBook", id));
    }

    private PriceBookEntry requireEntry(Long priceBookId, Long entryId) {
        return priceBookEntryRepository.findByIdAndPriceBookIdAndDeletedFalse(entryId, priceBookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PriceBookEntry",
                        "priceBookId=" + priceBookId + ", entryId=" + entryId
                ));
    }

    private void clearCurrentDefault(Long excludeId) {
        priceBookRepository.findByIsDefaultTrueAndDeletedFalse().ifPresent(existingDefault -> {
            if (excludeId == null || !excludeId.equals(existingDefault.getId())) {
                existingDefault.setIsDefault(false);
                priceBookRepository.save(existingDefault);
            }
        });
    }

    private void validateRegionUniqueness(String region, Long excludeId) {
        if (region == null) return;

        priceBookRepository.findByRegionAndDeletedFalse(region).ifPresent(existing -> {
            if (excludeId == null || !excludeId.equals(existing.getId())) {
                throw new BusinessException(
                        "A price book already exists for region " + region,
                        "DUPLICATE_REGION_PRICE_BOOK"
                );
            }
        });
    }

    private void validateDateRange(LocalDate effectiveDate, LocalDate expirationDate) {
        if (effectiveDate != null && expirationDate != null && expirationDate.isBefore(effectiveDate)) {
            throw new BusinessException(
                    "Expiration date must be on or after effective date",
                    "INVALID_DATE_RANGE"
            );
        }
    }

    private void ensureNotPublished(PriceBook priceBook) {
        LocalDate effectiveDate = priceBook.getEffectiveDate();
        boolean dateImpliesPublished = effectiveDate != null && !effectiveDate.isAfter(LocalDate.now());
        if (priceBook.isPublished() || dateImpliesPublished) {
            throw new BusinessException(
                    "Published price books and their entries are immutable",
                    "PRICE_BOOK_PUBLISHED_IMMUTABLE"
            );
        }
    }

    /**
     * DRG-431: entries are keyed by Sku + currency + UnitOfMeasure + EffectiveDateRange, and the natural
     * reading of that AC is that ranges for the same key must be non-overlapping — otherwise a resolution
     * date could land in more than one entry, and GetListPrice would have to guess. Rejects (400) a
     * create/update whose range overlaps any other non-deleted entry with the same Sku + currency +
     * UnitOfMeasure in this price book. See {@link EffectiveDateRange} for the overlap rule and the
     * precedent this follows.
     *
     * <p>To replace an open-ended entry with a new version, end-date the existing one first (update its
     * {@code effectiveEndDate}), then create the new entry — the same way {@code effectiveEndDate} is
     * documented to "shorten coverage without erasing history" elsewhere in this codebase
     * (contract-asset-management's {@code SubscriptionRatePeriod}).
     */
    private void validateNoOverlappingEntry(Long priceBookId, String sku, String currency, String unitOfMeasure,
                                             LocalDate effectiveStartDate, LocalDate effectiveEndDate,
                                             Long excludeEntryId) {
        List<PriceBookEntry> candidates = priceBookEntryRepository
                .findByPriceBookIdAndProductExternalIdAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                        priceBookId, sku, currency, unitOfMeasure);
        candidates.stream()
                .filter(entry -> excludeEntryId == null || !excludeEntryId.equals(entry.getId()))
                .filter(entry -> entry.overlapsRange(effectiveStartDate, effectiveEndDate))
                .findFirst()
                .ifPresent(conflict -> {
                    throw new BusinessException(
                            "An entry for SKU " + sku + " / " + currency + " / " + unitOfMeasure
                                    + " already covers an overlapping effective date range ["
                                    + describe(conflict.getEffectiveStartDate()) + ".."
                                    + describe(conflict.getEffectiveEndDate())
                                    + "]; end-date the existing entry before adding one with an overlapping range",
                            "OVERLAPPING_PRICE_BOOK_ENTRY_RANGE"
                    );
                });
    }

    private String describe(LocalDate date) {
        return date == null ? "open-ended" : date.toString();
    }

    private void validateEntryPriceRange(BigDecimal listPrice, BigDecimal floorPrice) {
        if (listPrice == null) {
            throw new BusinessException("List price is required", "MISSING_LIST_PRICE");
        }

        if (floorPrice != null && floorPrice.compareTo(listPrice) > 0) {
            throw new BusinessException(
                    "Floor price must be less than or equal to list price",
                    "INVALID_FLOOR_PRICE"
            );
        }
    }

    private void normalizeCreateRequest(PriceBookCreateRequest request) {
        request.setName(requireNonBlank(request.getName(), "Name is required", "INVALID_PRICE_BOOK_NAME"));
        request.setRegion(normalize(request.getRegion()));
        request.setCurrency(normalize(request.getCurrency()));
        if (request.getCurrency() == null) {
            request.setCurrency("USD");
        }
        if (request.getIsDefault() == null) {
            request.setIsDefault(false);
        }
    }

    private void normalizeUpdateRequest(PriceBookUpdateRequest request) {
        if (request.getName() != null) {
            request.setName(requireNonBlank(
                    request.getName(),
                    "Name must not be blank when provided",
                    "INVALID_PRICE_BOOK_NAME"
            ));
        }

        if (request.getRegion() != null) {
            request.setRegion(normalize(request.getRegion()));
        }

        if (request.getCurrency() != null) {
            request.setCurrency(requireNonBlank(
                    request.getCurrency(),
                    "Currency must not be blank when provided",
                    "INVALID_PRICE_BOOK_CURRENCY"
            ));
        }
    }

    private void normalizeEntryCreateRequest(PriceBookEntryCreateRequest request, PriceBook priceBook) {
        request.setProductExternalId(requireNonBlank(
                request.getProductExternalId(),
                "Product external ID is required",
                "INVALID_PRODUCT_EXTERNAL_ID"
        ));
        request.setCurrency(normalize(request.getCurrency()));
        if (request.getCurrency() == null) {
            request.setCurrency(priceBook.getCurrency());
        }
        request.setUnitOfMeasure(normalize(request.getUnitOfMeasure()));
        if (request.getUnitOfMeasure() == null) {
            request.setUnitOfMeasure("EACH");
        }
    }

    private void normalizeEntryUpdateRequest(PriceBookEntryUpdateRequest request) {
        if (request.getProductExternalId() != null) {
            request.setProductExternalId(requireNonBlank(
                    request.getProductExternalId(),
                    "Product external ID must not be blank when provided",
                    "INVALID_PRODUCT_EXTERNAL_ID"
            ));
        }

        if (request.getCurrency() != null) {
            request.setCurrency(requireNonBlank(
                    request.getCurrency(),
                    "Currency must not be blank when provided",
                    "INVALID_PRICE_BOOK_ENTRY_CURRENCY"
            ));
        }

        if (request.getUnitOfMeasure() != null) {
            request.setUnitOfMeasure(requireNonBlank(
                    request.getUnitOfMeasure(),
                    "Unit of measure must not be blank when provided",
                    "INVALID_PRICE_BOOK_ENTRY_UOM"
            ));
        }
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireNonBlank(String value, String message, String errorCode) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BusinessException(message, errorCode);
        }
        return normalized;
    }
}
