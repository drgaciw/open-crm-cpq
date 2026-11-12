package com.opencrm.pricebook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CostBookEntryRepository extends JpaRepository<CostBookEntry, Long> {

    Optional<CostBookEntry> findByIdAndDeletedFalse(Long id);

    @Query("select entry from CostBookEntry entry where entry.id = :id and entry.costBook.id = :costBookId and entry.deleted = false")
    Optional<CostBookEntry> findByIdAndCostBookIdAndDeletedFalse(@Param("id") Long id, @Param("costBookId") Long costBookId);

    @Query("select entry from CostBookEntry entry where entry.costBook.id = :costBookId and entry.deleted = false")
    Page<CostBookEntry> findByCostBookIdAndDeletedFalse(@Param("costBookId") Long costBookId, Pageable pageable);

    /**
     * DRG-431: candidate rows for GetCost. A SKU/currency/UOM combination may have several date-ranged
     * entries; the caller picks the one whose EffectiveDateRange covers the resolution date.
     */
    @Query("select entry from CostBookEntry entry where entry.costBook.id = :costBookId "
            + "and entry.sku = :sku and entry.currency = :currency "
            + "and entry.unitOfMeasure = :unitOfMeasure and entry.deleted = false")
    List<CostBookEntry> findByCostBookIdAndSkuAndCurrencyAndUnitOfMeasureAndDeletedFalse(
            @Param("costBookId") Long costBookId, @Param("sku") String sku,
            @Param("currency") String currency, @Param("unitOfMeasure") String unitOfMeasure);
}
