package com.opencrm.pricebook;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PriceBookService {

    private final PriceBookRepository priceBookRepository;
    private final PriceBookEntryRepository priceBookEntryRepository;
    private final PriceBookMapper priceBookMapper;

    public PriceBookDto create(PriceBookCreateRequest request) {
        normalizeCreateRequest(request);
        validateDateRange(request.getEffectiveDate(), request.getExpirationDate());
        validateRegionUniqueness(request.getRegion(), null);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearCurrentDefault(null);
        }

        PriceBook priceBook = priceBookMapper.toEntity(request);
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
        PriceBook existing = requirePriceBook(id);
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
        PriceBook updated = priceBookRepository.save(existing);
        log.info("Updated price book with ID: {}", updated.getId());
        return priceBookMapper.toDto(updated);
    }

    public void delete(Long id) {
        PriceBook existing = requirePriceBook(id);
        existing.softDelete();
        priceBookRepository.save(existing);
        log.info("Deleted price book with ID: {}", id);
    }

    public PriceBookEntryDto createEntry(Long priceBookId, PriceBookEntryCreateRequest request) {
        PriceBook priceBook = requirePriceBook(priceBookId);
        normalizeEntryCreateRequest(request, priceBook);
        validateEntryPriceRange(request.getListPrice(), request.getFloorPrice());

        if (priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndDeletedFalse(
                priceBookId,
                request.getProductExternalId()
        ).isPresent()) {
            throw new BusinessException(
                    "Product " + request.getProductExternalId() + " already exists in this price book",
                    "DUPLICATE_PRICE_BOOK_ENTRY"
            );
        }

        PriceBookEntry entry = priceBookMapper.toEntryEntity(request);
        entry.setPriceBookId(priceBookId);
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
        requirePriceBook(priceBookId);
        PriceBookEntry entry = requireEntry(priceBookId, entryId);
        normalizeEntryUpdateRequest(request);

        if (request.getProductExternalId() != null
                && !request.getProductExternalId().equals(entry.getProductExternalId())
                && priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndDeletedFalse(
                priceBookId,
                request.getProductExternalId()
        ).isPresent()) {
            throw new BusinessException(
                    "Product " + request.getProductExternalId() + " already exists in this price book",
                    "DUPLICATE_PRICE_BOOK_ENTRY"
            );
        }

        BigDecimal targetListPrice = request.getListPrice() != null ? request.getListPrice() : entry.getListPrice();
        BigDecimal targetFloorPrice = request.getFloorPrice() != null ? request.getFloorPrice() : entry.getFloorPrice();
        validateEntryPriceRange(targetListPrice, targetFloorPrice);

        priceBookMapper.updateEntryEntity(request, entry);
        PriceBookEntry updated = priceBookEntryRepository.save(entry);
        log.info("Updated price book entry with ID: {} for price book ID: {}", entryId, priceBookId);
        return priceBookMapper.toEntryDto(updated);
    }

    public void deleteEntry(Long priceBookId, Long entryId) {
        requirePriceBook(priceBookId);
        PriceBookEntry entry = requireEntry(priceBookId, entryId);
        entry.softDelete();
        priceBookEntryRepository.save(entry);
        log.info("Deleted price book entry with ID: {} for price book ID: {}", entryId, priceBookId);
    }

    private PriceBook requirePriceBook(Long id) {
        return priceBookRepository.findByIdAndDeletedFalse(id)
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
