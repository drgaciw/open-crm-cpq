package com.opencrm.quote.approval.recovery;

import com.opencrm.quote.quote.Quote;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** A keyset sweep prevents a failed or still-pending item from starving later quotes. */
public interface ApprovalProjectionRepository extends Repository<Quote, Long> {
    @Transactional(readOnly = true)
    @Query("select q from Quote q where q.deleted = false and q.status = com.opencrm.quote.quote.QuoteStatus.PENDING_APPROVAL "
            + "and q.approvalSubmissionId is not null and q.id > :afterId order by q.id")
    List<Quote> pendingAfter(long afterId, Pageable page);
}
