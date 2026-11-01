package com.opencrm.quote.ramp;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.lineitem.QuoteLineItemService;
import com.opencrm.quote.quote.QuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Preview expansion. Reads the quote so a missing id is a 404, and writes nothing.
 */
@Service
@RequiredArgsConstructor
public class RampScheduleQueryService {

    private final QuoteRepository quoteRepository;
    private final RampPricingProperties properties;
    private final RampEscalatorExpander expander;
    private final QuoteLineItemService lineItemService;

    @Transactional(readOnly = true)
    public RampScheduleDto expand(Long quoteId, RampExpandRequest request) {
        if (quoteRepository.findByIdAndDeletedFalse(quoteId).isEmpty()) {
            throw new ResourceNotFoundException("Quote", quoteId);
        }
        if (!properties.isEnabled()) {
            throw RampScheduleException.notSupported();
        }
        int duration = request.getDurationInMonths() == null ? 0 : request.getDurationInMonths();
        List<RampStepDto> steps = expander.expand(duration, request.getEscalator(), request.getQuantity(),
                properties.getMaxPeriods());
        if (steps.size() > properties.getMaxPeriods()) {
            throw RampScheduleException.invalid(List.of(RampReasons.TOO_MANY_PERIODS));
        }
        return RampScheduleDto.builder()
                .steps(steps)
                .escalator(request.getEscalator())
                .build();
    }

    /**
     * Existing steps stay as submitted; new steps continue from the last absolute unit.
     * The line/version pair opts into an atomic schedule and term save.
     */
    @Transactional
    public RampScheduleDto extend(Long quoteId, RampExtendRequest request) {
        if ((request.getLineItemId() == null) != (request.getExpectedQuoteVersion() == null)) {
            throw new BusinessException("lineItemId and expectedQuoteVersion must be supplied together", "VALIDATION_ERROR");
        }
        if (quoteRepository.findByIdAndDeletedFalse(quoteId).isEmpty()) {
            throw new ResourceNotFoundException("Quote", quoteId);
        }
        if (!properties.isEnabled()) {
            throw RampScheduleException.notSupported();
        }
        int duration = request.getDurationInMonths() == null ? 0 : request.getDurationInMonths();
        List<RampStepDto> steps = expander.extend(
                request.getSteps() == null ? List.of() : request.getSteps(),
                duration,
                request.getEscalator(), properties.getMaxPeriods());
        if (steps.size() > properties.getMaxPeriods()) {
            throw RampScheduleException.invalid(List.of(RampReasons.TOO_MANY_PERIODS));
        }
        RampScheduleDto schedule = RampScheduleDto.builder()
                .steps(steps)
                .escalator(request.getEscalator())
                .build();
        if (request.getLineItemId() != null) {
            var saved = lineItemService.persistExtendedRamp(quoteId, request.getLineItemId(), duration,
                    request.getExpectedQuoteVersion(), schedule);
            schedule.setDurationInMonths(duration);
            schedule.setQuoteVersion(saved.getVersion());
            schedule.setQuoteStatus(saved.getStatus());
        }
        return schedule;
    }
}
