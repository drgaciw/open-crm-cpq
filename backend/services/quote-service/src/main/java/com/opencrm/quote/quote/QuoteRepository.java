package com.opencrm.quote.quote;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {

    Optional<Quote> findByIdAndDeletedFalse(Long id);

    Optional<Quote> findByUuidAndDeletedFalse(String uuid);

    Optional<Quote> findByQuoteNumberAndDeletedFalse(String quoteNumber);

    Page<Quote> findByDeletedFalse(Pageable pageable);

    Page<Quote> findByStatusAndDeletedFalse(QuoteStatus status, Pageable pageable);

    Page<Quote> findByCustomerExternalIdAndDeletedFalse(String customerExternalId, Pageable pageable);

    Page<Quote> findByDealTypeAndDeletedFalse(DealType dealType, Pageable pageable);

    Page<Quote> findByApprovalStateAndDeletedFalse(ApprovalState approvalState, Pageable pageable);

    @Query("SELECT COALESCE(MAX(CAST(SUBSTRING(q.quoteNumber, 12) AS int)), 0) FROM Quote q WHERE q.quoteNumber LIKE :prefix")
    int findMaxSequenceByPrefix(String prefix);
}
