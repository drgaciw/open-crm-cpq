package com.opencrm.approval.repository;

import com.opencrm.approval.domain.ApprovalTimelineEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalTimelineRepository extends JpaRepository<ApprovalTimelineEntry, Long> {

    List<ApprovalTimelineEntry> findByQuoteIdAndDeletedFalseOrderByOccurredAtAscIdAsc(String quoteId);

    List<ApprovalTimelineEntry> findByApprovalRequestIdAndDeletedFalseOrderByOccurredAtAscIdAsc(Long approvalRequestId);
}
