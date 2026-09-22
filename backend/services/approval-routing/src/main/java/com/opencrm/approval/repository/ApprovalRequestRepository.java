package com.opencrm.approval.repository;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {
    
    List<ApprovalRequest> findByQuoteIdAndDeletedFalseOrderBySubmittedAtDescIdDesc(String quoteId);

    List<ApprovalRequest> findByQuoteIdAndStatus(String quoteId, ApprovalStatus status);
    
    List<ApprovalRequest> findByStatus(ApprovalStatus status);
    
    List<ApprovalRequest> findByApprovalTierAndStatus(String approvalTier, ApprovalStatus status);
}
