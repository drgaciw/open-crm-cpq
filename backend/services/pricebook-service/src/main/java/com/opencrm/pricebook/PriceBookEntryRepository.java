package com.opencrm.pricebook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceBookEntryRepository extends JpaRepository<PriceBookEntry, Long> {

    Optional<PriceBookEntry> findByIdAndDeletedFalse(Long id);

    @Query("select entry from PriceBookEntry entry where entry.id = :id and entry.priceBook.id = :priceBookId and entry.deleted = false")
    Optional<PriceBookEntry> findByIdAndPriceBookIdAndDeletedFalse(@Param("id") Long id, @Param("priceBookId") Long priceBookId);

    @Query("select entry from PriceBookEntry entry where entry.priceBook.id = :priceBookId and entry.deleted = false")
    List<PriceBookEntry> findByPriceBookIdAndDeletedFalse(@Param("priceBookId") Long priceBookId);

    @Query("select entry from PriceBookEntry entry where entry.priceBook.id = :priceBookId and entry.deleted = false")
    Page<PriceBookEntry> findByPriceBookIdAndDeletedFalse(@Param("priceBookId") Long priceBookId, Pageable pageable);

    @Query("select entry from PriceBookEntry entry where entry.priceBook.id = :priceBookId and entry.productExternalId = :productExternalId and entry.deleted = false")
    Optional<PriceBookEntry> findByPriceBookIdAndProductExternalIdAndDeletedFalse(
            @Param("priceBookId") Long priceBookId, @Param("productExternalId") String productExternalId);

    /**
     * Batch lookup for resolve-batch: one IN query instead of N per-product finds.
     */
    @Query("select entry from PriceBookEntry entry where entry.priceBook.id = :priceBookId and entry.productExternalId in :productExternalIds and entry.deleted = false")
    List<PriceBookEntry> findByPriceBookIdAndProductExternalIdInAndDeletedFalse(
            @Param("priceBookId") Long priceBookId, @Param("productExternalIds") Collection<String> productExternalIds);

    /**
     * DRG-431: candidate rows for GetListPrice. A SKU/currency/UOM combination may have several date-ranged
     * entries; the caller picks the one whose EffectiveDateRange covers the resolution date.
     */
    @Query("select entry from PriceBookEntry entry where entry.priceBook.id = :priceBookId "
            + "and entry.productExternalId = :sku and entry.currency = :currency "
            + "and entry.unitOfMeasure = :unitOfMeasure and entry.deleted = false")
    List<PriceBookEntry> findByPriceBookIdAndProductExternalIdAndCurrencyAndUnitOfMeasureAndDeletedFalse(
            @Param("priceBookId") Long priceBookId, @Param("sku") String sku,
            @Param("currency") String currency, @Param("unitOfMeasure") String unitOfMeasure);
}
