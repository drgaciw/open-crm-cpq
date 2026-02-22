package com.opencrm.quote.approval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    Optional<ApprovalRequest> findByIdAndDeletedFalse(Long id);

    List<ApprovalRequest> findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(Long quoteId);

    List<ApprovalRequest> findByStatusAndDeletedFalse(ApprovalStatus status);

    Optional<ApprovalRequest> findFirstByQuoteIdAndStatusAndDeletedFalse(Long quoteId, ApprovalStatus status);
}
