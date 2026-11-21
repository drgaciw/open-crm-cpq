package com.opencrm.quote.approval.recovery;

import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.quote.*;
import com.opencrm.quote.ramp.RampPricingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** Applies a canonical terminal decision only to its unchanged pending projection. */
@Service
@RequiredArgsConstructor
public class ApprovalProjectionWriter {
    private final QuoteRepository quotes;

    public enum Outcome { APPLIED, PENDING, ALREADY_FINAL, CANONICAL_MISMATCH, UNSUPPORTED_STATUS, QUOTE_MISSING, STALE_BINDING }

    @Transactional
    public Outcome reconcile(Long quoteId, Long observedVersion, Long submissionId, RoutingApprovalDto canonical) {
        if (canonical == null || !Objects.equals(submissionId, canonical.getId())
                || !String.valueOf(quoteId).equals(canonical.getQuoteId())) return Outcome.CANONICAL_MISMATCH;
        if ("PENDING".equals(canonical.getStatus())) return Outcome.PENDING;
        if (!"APPROVED".equals(canonical.getStatus()) && !"REJECTED".equals(canonical.getStatus())) return Outcome.UNSUPPORTED_STATUS;
        Quote quote = quotes.findByIdAndDeletedFalse(quoteId).orElse(null);
        if (quote == null) return Outcome.QUOTE_MISSING;
        if (quote.getStatus() != QuoteStatus.PENDING_APPROVAL) return Outcome.ALREADY_FINAL;
        if (!Objects.equals(observedVersion, quote.getVersion())
                || !Objects.equals(submissionId, quote.getApprovalSubmissionId())
                || quote.getApprovalSubmissionVersion() == null || quote.getVersion() == null
                || quote.getVersion() != quote.getApprovalSubmissionVersion() + 1
                || !Objects.equals(quote.getApprovalTier(), canonical.getApprovalTier())
                || quote.isRampScheduleStale() || RampPricingSnapshot.fingerprint(quote) != null) return Outcome.STALE_BINDING;
        quote.setStatus(QuoteStatus.valueOf(canonical.getStatus()));
        quote.setApprovalState(ApprovalState.valueOf(canonical.getStatus()));
        // JPA @Version fences a concurrent mutation after the checks above, including another worker.
        quotes.saveAndFlush(quote);
        return Outcome.APPLIED;
    }
}
