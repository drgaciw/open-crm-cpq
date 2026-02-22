package com.opencrm.quote.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuoteDocumentRepository extends JpaRepository<QuoteDocument, Long> {

    Optional<QuoteDocument> findByIdAndDeletedFalse(Long id);

    List<QuoteDocument> findByQuoteIdAndDeletedFalse(Long quoteId);
}
