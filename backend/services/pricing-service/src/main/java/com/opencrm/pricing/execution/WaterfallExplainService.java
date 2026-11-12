package com.opencrm.pricing.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.waterfall.PricingResult;
import com.opencrm.pricing.waterfall.PricingWaterfall;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explains persisted calculations; never executes today's rules against historical input. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WaterfallExplainService {
    private final PricingAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public PricingWaterfall explain(String quoteExternalId, Long auditId) {
        PricingAuditLog audit;
        if (auditId != null) {
            audit = auditLogRepository.findById(auditId)
                    .filter(row -> quoteExternalId.equals(row.getQuoteExternalId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Pricing waterfall for quote " + quoteExternalId));
        } else {
            // ID breaks timestamp ties deterministically (fixed clocks can produce identical instants).
            audit = auditLogRepository.findByQuoteExternalIdOrderByExecutedAtDesc(quoteExternalId).stream()
                    .max(Comparator.comparing(PricingAuditLog::getExecutedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                            .thenComparing(PricingAuditLog::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow(() -> new ResourceNotFoundException("Pricing waterfall for quote " + quoteExternalId));
        }
        try {
            PricingWaterfall waterfall = audit.getWaterfallTrace() != null && !audit.getWaterfallTrace().isBlank()
                    ? objectMapper.readValue(audit.getWaterfallTrace(), PricingWaterfall.class)
                    : objectMapper.readValue(audit.getOutputSnapshot(), PricingResult.class).getWaterfall();
            if (waterfall == null || waterfall.getSteps() == null) {
                throw new BusinessException("Recorded pricing waterfall is missing", "AUDIT_WATERFALL_INVALID");
            }
            return waterfall; // Array/list order is the recorded execution order, not step-type order.
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException("Recorded pricing waterfall is unreadable", "AUDIT_WATERFALL_INVALID");
        }
    }
}
