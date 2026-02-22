package com.opencrm.quote.lineitem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteLineItemRepository extends JpaRepository<QuoteLineItem, Long> {

    Optional<QuoteLineItem> findByIdAndDeletedFalse(Long id);

    List<QuoteLineItem> findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(Long quoteId);
}
