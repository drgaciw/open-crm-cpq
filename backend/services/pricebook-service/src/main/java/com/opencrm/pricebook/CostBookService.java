package com.opencrm.pricebook;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * DRG-431 / BC-03-01: DefineCostBook / PublishCostBookVersion / GetCost. Mirrors {@link PriceBookService}'s
 * lifecycle and resolution shape, but for costs, so costs can be defined, versioned and published independently
 * of list prices.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CostBookService {

    private final CostBookRepository costBookRepository;
    private final CostBookEntryRepository costBookEntryRepository;
    private final CostBookMapper costBookMapper;

    public CostBookDto create(CostBookCreateRequest request) {
        normalizeCreateRequest(request);
        validateDateRange(request.getEffectiveDate(), request.getExpirationDate());

        CostBook costBook = costBookMapper.toEntity(request);
        CostBook saved = costBookRepository.save(costBook);
        log.info("Created cost book with ID: {}", saved.getId());
        return costBookMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<CostBookDto> list(Pageable pageable) {
        return costBookRepository.findByDeletedFalse(pageable).map(costBookMapper::toDto);
    }

    @Transactional(readOnly = true)
    public CostBookDto getById(Long id) {
        return costBookMapper.toDto(requireCostBook(id));
    }

    public CostBookDto update(Long id, CostBookUpdateRequest request) {
        CostBook existing = requireCostBookForUpdate(id);
        ensureNotPublished(existing);

        LocalDate targetEffectiveDate =
                request.getEffectiveDate() != null ? request.getEffectiveDate() : existing.getEffectiveDate();
        LocalDate targetExpirationDate =
                request.getExpirationDate() != null ? request.getExpirationDate() : existing.getExpirationDate();
        validateDateRange(targetEffectiveDate, targetExpirationDate);

        costBookMapper.updateEntity(request, existing);
        CostBook updated = costBookRepository.save(existing);
        log.info("Updated cost book with ID: {}", updated.getId());
        return costBookMapper.toDto(updated);
    }

    public void delete(Long id) {
        CostBook existing = requireCostBookForUpdate(id);
        ensureNotPublished(existing);
        existing.softDelete();
        costBookRepository.save(existing);
        log.info("Deleted cost book with ID: {}", id);
    }

    /**
     * PublishCostBookVersion: makes this cost book version immutable. Unlike a price book, publishing a cost
     * book does not itself emit a domain event (only PriceBookPublished is part of this story's contract);
     * list prices and costs stay separable because a price book links to a cost book by id, not by copying
     * its entries.
     */
    public CostBookDto publish(Long id) {
        CostBook costBook = requireCostBookForUpdate(id);
        if (costBook.isPublished()) {
            throw new BusinessException("Cost book is already published", "COST_BOOK_ALREADY_PUBLISHED");
        }
        costBook.setStatus(PriceBookStatus.PUBLISHED);
        costBook.setPublishedAt(Instant.now());
        CostBook saved = costBookRepository.save(costBook);
        log.info("Published cost book with ID: {} (version {})", saved.getId(), saved.getVersionNumber());
        return costBookMapper.toDto(saved);
    }

    public CostBookEntryDto createEntry(Long costBookId, CostBookEntryCreateRequest request) {
        CostBook costBook = requireCostBookForUpdate(costBookId);
        ensureNotPublished(costBook);
        normalizeEntryCreateRequest(request, costBook);
        validateNoOverlappingEntry(costBookId, request.getSku(), request.getCurrency(), request.getUnitOfMeasure(),
                request.getEffectiveStartDate(), request.getEffectiveEndDate(), null);

        CostBookEntry entry = costBookMapper.toEntryEntity(request);
        entry.setCostBook(costBook);
        CostBookEntry saved = costBookEntryRepository.save(entry);
        log.info("Created cost book entry with ID: {} for cost book ID: {}", saved.getId(), costBookId);
        return costBookMapper.toEntryDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<CostBookEntryDto> listEntries(Long costBookId, Pageable pageable) {
        requireCostBook(costBookId);
        return costBookEntryRepository.findByCostBookIdAndDeletedFalse(costBookId, pageable)
                .map(costBookMapper::toEntryDto);
    }

    @Transactional(readOnly = true)
    public CostBookEntryDto getEntry(Long costBookId, Long entryId) {
        requireCostBook(costBookId);
        CostBookEntry entry = requireEntry(costBookId, entryId);
        return costBookMapper.toEntryDto(entry);
    }

    public CostBookEntryDto updateEntry(Long costBookId, Long entryId, CostBookEntryUpdateRequest request) {
        CostBook costBook = requireCostBookForUpdate(costBookId);
        ensureNotPublished(costBook);
        CostBookEntry entry = requireEntry(costBookId, entryId);
        normalizeEntryUpdateRequest(request);

        String targetSku = request.getSku() != null ? request.getSku() : entry.getSku();
        String targetCurrency = request.getCurrency() != null ? request.getCurrency() : entry.getCurrency();
        String targetUom = request.getUnitOfMeasure() != null ? request.getUnitOfMeasure() : entry.getUnitOfMeasure();
        LocalDate targetStart = request.getEffectiveStartDate() != null
                ? request.getEffectiveStartDate() : entry.getEffectiveStartDate();
        LocalDate targetEnd = request.getEffectiveEndDate() != null
                ? request.getEffectiveEndDate() : entry.getEffectiveEndDate();
        validateNoOverlappingEntry(costBookId, targetSku, targetCurrency, targetUom, targetStart, targetEnd, entryId);

        costBookMapper.updateEntryEntity(request, entry);
        CostBookEntry updated = costBookEntryRepository.save(entry);
        log.info("Updated cost book entry with ID: {} for cost book ID: {}", entryId, costBookId);
        return costBookMapper.toEntryDto(updated);
    }

    public void deleteEntry(Long costBookId, Long entryId) {
        CostBook costBook = requireCostBookForUpdate(costBookId);
        ensureNotPublished(costBook);
        CostBookEntry entry = requireEntry(costBookId, entryId);
        entry.softDelete();
        costBookEntryRepository.save(entry);
        log.info("Deleted cost book entry with ID: {} for cost book ID: {}", entryId, costBookId);
    }

    /**
     * GetCost: resolves the cost for a SKU, currency and unit of measure as of a date, picking among the
     * candidate entries (there may be several date-ranged rows for the same SKU/currency/UOM) the one whose
     * EffectiveDateRange covers {@code asOfDate}. Entry authoring rejects overlapping ranges for the same
     * key ({@link #validateNoOverlappingEntry}), so at most one candidate can cover any given date; the
     * "most recently started" pick below is then provably correct, not a tie-break over ambiguous data —
     * it only matters for rows written before this validation existed or outside it (direct SQL, etc.).
     */
    @Transactional(readOnly = true)
    public PriceResolutionDto getCost(Long costBookId, String sku, String currency, String unitOfMeasure,
                                       LocalDate asOfDate) {
        requireCostBook(costBookId);
        LocalDate resolutionDate = asOfDate != null ? asOfDate : LocalDate.now();
        String normalizedUom = unitOfMeasure != null ? unitOfMeasure : "EACH";

        List<CostBookEntry> candidates = costBookEntryRepository
                .findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                        costBookId, sku, currency, normalizedUom);

        return candidates.stream()
                .filter(entry -> entry.isEffectiveOn(resolutionDate))
                .max(Comparator.comparing(CostBookEntry::getEffectiveStartDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(entry -> PriceResolutionDto.builder()
                        .bookId(costBookId)
                        .sku(sku)
                        .currency(currency)
                        .unitOfMeasure(normalizedUom)
                        .asOfDate(resolutionDate)
                        .matched(true)
                        .amount(entry.getCost())
                        .build())
                .orElseGet(() -> PriceResolutionDto.builder()
                        .bookId(costBookId)
                        .sku(sku)
                        .currency(currency)
                        .unitOfMeasure(normalizedUom)
                        .asOfDate(resolutionDate)
                        .matched(false)
                        .build());
    }

    CostBook requireCostBook(Long id) {
        return costBookRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostBook", id));
    }

    private CostBook requireCostBookForUpdate(Long id) {
        return costBookRepository.findWithLockByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("CostBook", id));
    }

    private CostBookEntry requireEntry(Long costBookId, Long entryId) {
        return costBookEntryRepository.findByIdAndCostBookIdAndDeletedFalse(entryId, costBookId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "CostBookEntry", "costBookId=" + costBookId + ", entryId=" + entryId));
    }

    private void validateDateRange(LocalDate effectiveDate, LocalDate expirationDate) {
        if (effectiveDate != null && expirationDate != null && expirationDate.isBefore(effectiveDate)) {
            throw new BusinessException(
                    "Expiration date must be on or after effective date", "INVALID_DATE_RANGE");
        }
    }

    private void ensureNotPublished(CostBook costBook) {
        if (costBook.isPublished()) {
            throw new BusinessException(
                    "Published cost books and their entries are immutable", "COST_BOOK_PUBLISHED_IMMUTABLE");
        }
    }

    /**
     * DRG-431: entries are keyed by Sku + currency + UnitOfMeasure + EffectiveDateRange, and the natural
     * reading of that AC is that ranges for the same key must be non-overlapping — otherwise a resolution
     * date could land in more than one entry, and GetCost would have to guess. Rejects (400) a
     * create/update whose range overlaps any other non-deleted entry with the same Sku + currency +
     * UnitOfMeasure in this cost book. See {@link EffectiveDateRange} for the overlap rule.
     *
     * <p>To replace an open-ended entry with a new version, end-date the existing one first (update its
     * {@code effectiveEndDate}), then create the new entry.
     */
    private void validateNoOverlappingEntry(Long costBookId, String sku, String currency, String unitOfMeasure,
                                             LocalDate effectiveStartDate, LocalDate effectiveEndDate,
                                             Long excludeEntryId) {
        List<CostBookEntry> candidates = costBookEntryRepository
                .findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
                        costBookId, sku, currency, unitOfMeasure);
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
                            "OVERLAPPING_COST_BOOK_ENTRY_RANGE"
                    );
                });
    }

    private String describe(LocalDate date) {
        return date == null ? "open-ended" : date.toString();
    }

    private void normalizeCreateRequest(CostBookCreateRequest request) {
        request.setName(requireNonBlank(request.getName(), "Name is required", "INVALID_COST_BOOK_NAME"));
        request.setRegion(normalize(request.getRegion()));
        request.setCurrency(normalize(request.getCurrency()));
        if (request.getCurrency() == null) {
            request.setCurrency("USD");
        }
    }

    private void normalizeEntryCreateRequest(CostBookEntryCreateRequest request, CostBook costBook) {
        request.setSku(requireNonBlank(request.getSku(), "SKU is required", "INVALID_SKU"));
        request.setCurrency(normalize(request.getCurrency()));
        if (request.getCurrency() == null) {
            request.setCurrency(costBook.getCurrency());
        }
        request.setUnitOfMeasure(normalize(request.getUnitOfMeasure()));
        if (request.getUnitOfMeasure() == null) {
            request.setUnitOfMeasure("EACH");
        }
    }

    private void normalizeEntryUpdateRequest(CostBookEntryUpdateRequest request) {
        if (request.getSku() != null) {
            request.setSku(requireNonBlank(request.getSku(), "SKU must not be blank when provided", "INVALID_SKU"));
        }
        if (request.getCurrency() != null) {
            request.setCurrency(requireNonBlank(
                    request.getCurrency(), "Currency must not be blank when provided", "INVALID_COST_BOOK_ENTRY_CURRENCY"));
        }
        if (request.getUnitOfMeasure() != null) {
            request.setUnitOfMeasure(requireNonBlank(
                    request.getUnitOfMeasure(), "Unit of measure must not be blank when provided",
                    "INVALID_COST_BOOK_ENTRY_UOM"));
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
