package com.opencrm.pricebook;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PriceBookEntryRepository extends JpaRepository<PriceBookEntry, Long> {

    Optional<PriceBookEntry> findByIdAndDeletedFalse(Long id);

    Optional<PriceBookEntry> findByIdAndPriceBookIdAndDeletedFalse(Long id, Long priceBookId);

    List<PriceBookEntry> findByPriceBookIdAndDeletedFalse(Long priceBookId);

    Page<PriceBookEntry> findByPriceBookIdAndDeletedFalse(Long priceBookId, Pageable pageable);

    List<PriceBookEntry> findByPriceBookIdAndProductExternalIdInAndDeletedFalse(Long priceBookId, List<String> productExternalIds);

    Optional<PriceBookEntry> findByPriceBookIdAndProductExternalIdAndDeletedFalse(
            Long priceBookId, String productExternalId);
}
